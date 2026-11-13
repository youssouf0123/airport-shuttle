package com.airportshuttle;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;

@Service
public class FxRateService {
	public record Rate(double usdToXof, Instant sourceUpdatedAt, Instant fetchedAt, String source) {}
	private static final Logger log = LoggerFactory.getLogger(FxRateService.class);
	private final JdbcTemplate db;
	private final RestClient client;
	private final AtomicReference<Rate> current = new AtomicReference<>(new Rate(500d, null, null, "fallback"));

	public FxRateService(JdbcTemplate db) {
		this.db = db;
		var http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
		var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(java.time.Duration.ofSeconds(5));
		this.client = RestClient.builder().baseUrl("https://open.er-api.com/v6/latest/USD").requestFactory(factory).build();
	}

	@PostConstruct
	void loadCachedRate() {
		try {
			var rows = db.queryForList("SELECT rate,source_updated_at,fetched_at FROM exchange_rates WHERE base_code='USD' AND target_code='XOF'");
			if (!rows.isEmpty()) {
				var row = rows.get(0);
				current.set(new Rate(((Number) row.get("rate")).doubleValue(),
						row.get("source_updated_at") == null ? null : ((java.sql.Timestamp) row.get("source_updated_at")).toInstant(),
						((java.sql.Timestamp) row.get("fetched_at")).toInstant(), "ExchangeRate-API"));
			}
		} catch (Exception ex) {
			log.warn("Could not load cached USD/XOF rate; using configured fallback.", ex);
		}
	}

	@Scheduled(initialDelay = 0, fixedDelay = 86_400_000)
	public void refreshDaily() {
		try {
			Map<?, ?> payload = client.get().retrieve().body(Map.class);
			if (payload == null || !"success".equals(payload.get("result")) || !(payload.get("rates") instanceof Map<?, ?> rates)
					|| !(rates.get("XOF") instanceof Number rate) || rate.doubleValue() <= 0)
				throw new IllegalStateException("Exchange rate response did not contain USD/XOF.");
			Instant sourceAt = payload.get("time_last_update_unix") instanceof Number timestamp
					? Instant.ofEpochSecond(timestamp.longValue()) : null;
			Instant fetchedAt = Instant.now();
			db.update("INSERT INTO exchange_rates(base_code,target_code,rate,source_updated_at,fetched_at) VALUES('USD','XOF',?,?,?) ON DUPLICATE KEY UPDATE rate=VALUES(rate),source_updated_at=VALUES(source_updated_at),fetched_at=VALUES(fetched_at)",
				rate.doubleValue(), sourceAt == null ? null : java.sql.Timestamp.from(sourceAt), java.sql.Timestamp.from(fetchedAt));
			current.set(new Rate(rate.doubleValue(), sourceAt, fetchedAt, "ExchangeRate-API"));
		} catch (Exception ex) {
			log.warn("USD/XOF rate refresh failed; continuing with the last cached rate ({})", current.get().usdToXof(), ex);
		}
	}

	public Rate current() { return current.get(); }
}
