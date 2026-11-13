package com.airportshuttle;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import nl.martijndwars.webpush.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.airportshuttle.Auth.*;

@RestController
@RequestMapping("/api/push")
public class Push {
	public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 200) String auth) {
	}

	public record Subscription(@NotBlank @Size(max = 1500) String endpoint, @NotNull @Valid Keys keys) {
	}

	public record Endpoint(@NotBlank @Size(max = 1500) String endpoint) {
	}

	private final JdbcTemplate db;
	private final ObjectMapper json;
	private PushService service;
	private String publicKey;

	public Push(JdbcTemplate db, ObjectMapper json) {
		this.db = db;
		this.json = json;
		java.security.Security.addProvider(new BouncyCastleProvider());
	}

	private synchronized void initialize() throws Exception {
		if (service != null)
			return;
		var values = db.queryForList("SELECT setting_value FROM app_settings WHERE setting_key='vapid'", String.class);
		String stored;
		if (values.isEmpty()) {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			KeyPair pair = generator.generateKeyPair();
			var encoder = Base64.getUrlEncoder().withoutPadding();
			stored = encoder.encodeToString(Utils.encode((ECPublicKey) pair.getPublic())) + ":"
					+ encoder.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));
			db.update("INSERT IGNORE INTO app_settings(setting_key,setting_value) VALUES('vapid',?)", stored);
		}
		stored = db.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key='vapid'", String.class);
		String[] parts = stored.split(":");
		publicKey = parts[0];
		service = new PushService(parts[0], parts[1], "mailto:operations@example.com");
	}

	@GetMapping("/key")
	public Map<String, String> key() throws Exception {
		initialize();
		return Map.of("publicKey", publicKey);
	}

	@PostMapping("/subscribe")
	public Map<String, Boolean> subscribe(@Valid @RequestBody Subscription s, HttpServletRequest request)
			throws Exception {
		User u = user(request);
		role(u, "PASSENGER");
		validateEndpoint(s.endpoint());
		try {
			Utils.loadPublicKey(s.keys().p256dh());
			if (Base64.getUrlDecoder().decode(s.keys().auth()).length != 16)
				throw new IllegalArgumentException();
		} catch (Exception ex) {
			throw error(400, "Invalid push subscription keys.");
		}
		String hash = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(s.endpoint().getBytes(StandardCharsets.UTF_8)));
		db.update(
				"INSERT INTO push_subscriptions(user_id,endpoint,endpoint_hash,p256dh,auth) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),p256dh=VALUES(p256dh),auth=VALUES(auth)",
				u.id(), s.endpoint(), hash, s.keys().p256dh(), s.keys().auth());
		return Map.of("ok", true);
	}

	@PostMapping("/unsubscribe")
	public Map<String, Boolean> unsubscribe(@Valid @RequestBody Endpoint e, HttpServletRequest request) {
		db.update("DELETE FROM push_subscriptions WHERE user_id=? AND endpoint=?", user(request).id(), e.endpoint());
		return Map.of("ok", true);
	}

	static void validateEndpoint(String endpoint) {
		try {
			URI uri = URI.create(endpoint);
			String host = uri.getHost();
			boolean allowed = host != null && (host.equals("fcm.googleapis.com")
					|| host.equals("updates.push.services.mozilla.com") || host.endsWith(".push.services.mozilla.com")
					|| host.equals("web.push.apple.com") || host.endsWith(".notify.windows.com"));
			if (!allowed || !"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1)
				throw new IllegalArgumentException();
		} catch (Exception ex) {
			throw error(400, "Unsupported browser push provider.");
		}
	}

	@Scheduled(fixedDelay = 10000, initialDelay = 15000)
	public void deliver() {
		try {
			if (db.queryForObject("SELECT COUNT(*) FROM push_subscriptions", Integer.class) == 0)
				return;
			initialize();
			db.update(
					"INSERT IGNORE INTO push_deliveries(notification_id,subscription_id) SELECT n.id,s.id FROM notifications n JOIN push_subscriptions s ON s.user_id=n.user_id WHERE n.created_at > CURRENT_TIMESTAMP - INTERVAL 10 MINUTE AND n.read_at IS NULL");
			var rows = db.queryForList(
					"SELECT d.notification_id,d.subscription_id,s.endpoint,s.p256dh,s.auth,n.message,n.trip_id FROM push_deliveries d JOIN push_subscriptions s ON s.id=d.subscription_id JOIN notifications n ON n.id=d.notification_id JOIN trips t ON t.id=n.trip_id WHERE d.delivered=false AND d.attempts<3 AND n.read_at IS NULL AND n.created_at > CURRENT_TIMESTAMP - INTERVAL 10 MINUTE AND t.status IN ('EN_ROUTE','ARRIVING') LIMIT 10");
			for (var row : rows) {
				db.update(
						"UPDATE push_deliveries SET attempts=attempts+1 WHERE notification_id=? AND subscription_id=?",
						row.get("notification_id"), row.get("subscription_id"));
				try {
					var notification = new Notification((String) row.get("endpoint"), (String) row.get("p256dh"),
							(String) row.get("auth"),
							json.writeValueAsBytes(Map.of("message", row.get("message"), "tripId", row.get("trip_id"))),
							300);
					int status = service.sendAsync(notification).get(8, TimeUnit.SECONDS).getStatusLine()
							.getStatusCode();
					if (status >= 200 && status < 300)
						db.update(
								"UPDATE push_deliveries SET delivered=true WHERE notification_id=? AND subscription_id=?",
								row.get("notification_id"), row.get("subscription_id"));
					else if (status == 404 || status == 410)
						db.update("DELETE FROM push_subscriptions WHERE id=?", row.get("subscription_id"));
				} catch (Exception ex) {
					org.slf4j.LoggerFactory.getLogger(Push.class).warn("Push delivery attempt failed: {}",
							ex.getClass().getSimpleName());
				}
			}
		} catch (Exception ex) {
			org.slf4j.LoggerFactory.getLogger(Push.class).warn("Push delivery unavailable: {}",
					ex.getClass().getSimpleName());
		}
	}
}
