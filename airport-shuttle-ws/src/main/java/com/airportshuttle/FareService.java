package com.airportshuttle;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FareService {
	public record Quote(double distanceKm, int usdCents, long xofAmount, double usdToXof, String rateSource) {}
	private final FxRateService rates;
	private final RestClient osrm;
	private final ConcurrentHashMap<String, CachedDistance> distances = new ConcurrentHashMap<>();
	private record CachedDistance(double km, long cachedAt) {}

	public FareService(FxRateService rates) {
		this.rates = rates;
		var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		var factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(4));
		this.osrm = RestClient.builder().baseUrl("https://router.project-osrm.org").requestFactory(factory).build();
	}

	public Quote quote(TripService.Location pickup, TripService.Location destination) {
		double km = routeDistanceKm(pickup, destination);
		int cents = (int) Math.max(0, Math.round(km * 100)); // $1 per kilometer
		var rate = rates.current();
		return new Quote(km, cents, Math.round(cents / 100d * rate.usdToXof()), rate.usdToXof(), rate.source());
	}

	private double routeDistanceKm(TripService.Location pickup, TripService.Location destination) {
		String key = pickup.id() + ":" + destination.id();
		CachedDistance cached = distances.get(key);
		if (cached != null && System.currentTimeMillis() - cached.cachedAt() < Duration.ofHours(24).toMillis()) return cached.km();
		double straight = haversineKm(pickup.lat(), pickup.lng(), destination.lat(), destination.lng());
		if (straight > 1500) throw Auth.error(400, "Pickup and destination are too far apart for an Airport Shuttle fare.");
		try {
			String coordinates = pickup.lng() + "," + pickup.lat() + ";" + destination.lng() + "," + destination.lat();
			Map<?, ?> response = osrm.get().uri("/route/v1/driving/" + coordinates + "?overview=false").retrieve().body(Map.class);
			if (response != null && "Ok".equals(response.get("code")) && response.get("routes") instanceof List<?> routes
					&& !routes.isEmpty() && routes.get(0) instanceof Map<?, ?> route && route.get("distance") instanceof Number meters)
				return cache(key, Math.max(0.1, meters.doubleValue() / 1000d));
		} catch (Exception ignored) { /* Use a bounded straight-line estimate if OSRM is temporarily unavailable. */ }
		return cache(key, Math.max(0.1, straight * 1.25));
	}

	private double cache(String key, double km) { distances.put(key, new CachedDistance(km, System.currentTimeMillis())); return km; }

	private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
