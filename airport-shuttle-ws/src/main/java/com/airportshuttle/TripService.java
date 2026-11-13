package com.airportshuttle;

import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import static com.airportshuttle.Auth.*;

@Service
public class TripService {
	public record Location(long id, String name, String detail, double lat, double lng, boolean airport) {
	}

	public record Trip(long id, long passengerId, String passengerName, Long driverId, String driverName,
			String vehicle, Location pickup, Location destination, Location via, String flight, Instant pickupAt,
			int passengers, int bags, String notes, String dispatchNotes, String status, int fareCents, Double distanceKm,
			double usdToXof, long fareXof, int version,
			Double driverLat, Double driverLng, Instant locationAt, Integer etaMinutes, String passengerPhone,
			Instant arrivedAt, Integer waitSeconds, String cancellationReason,
			Instant noShowAvailableAt,
			String driverPhone, String driverEmail, Instant createdAt,
			Instant updatedAt) {
	}

	public record AssignmentOption(long driverId, String driverName, String vehicle, boolean available,
			boolean capacityAvailable, int vehicleCapacity, int passengersNeeded,
			int conflictCount, Instant nextConflictAt) {}

	public record HistoryTrip(Trip trip, String passengerEmail, String passengerPhone, String driverEmail, String driverPhone, String driverAddress) {}
	public record HistoryPage(List<HistoryTrip> content, int page, int size, long totalElements, int totalPages) {}

	public record Booking(@Min(1) long pickupId, @Min(1) long destinationId, @NotBlank @Size(max = 20) String flight,
			@NotNull Instant pickupAt, @Min(1) @Max(8) int passengers, @Min(0) @Max(12) int bags,
			@NotNull @Size(max = 500) String notes, @NotBlank @Size(max = 80) String requestKey) {
	}

	public record Assign(@Min(1) long driverId, @Min(0) int version) {
	}

	public record Status(@NotBlank String status, @Min(0) int version) {
	}

	public record Route(@Min(1) long pickupId, @Min(1) long destinationId, Long viaId,
			@NotNull @Size(max = 500) String notes, @Min(0) int version) {
	}

	public record Position(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng, @Min(0) @Max(240) Integer etaMinutes) {
	}

	private final JdbcTemplate db;
	private final FareService fares;
	@Value("${shuttle.wait.grace-minutes:10}")
	private int waitGraceMinutes;
	private static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED");
	private static final Map<String, String> NEXT = Map.of("ASSIGNED", "ACCEPTED", "ACCEPTED", "EN_ROUTE", "EN_ROUTE",
			"ARRIVING", "ARRIVING", "BOARDING", "BOARDING", "IN_TRANSIT", "IN_TRANSIT", "COMPLETED");

	public TripService(JdbcTemplate db, FareService fares) {
		this.db = db;
		this.fares = fares;
	}

	public List<Location> locations() {
		return db.query("SELECT * FROM locations ORDER BY id",
				(r, n) -> new Location(r.getLong("id"), r.getString("name"), r.getString("detail"), r.getDouble("lat"),
						r.getDouble("lng"), r.getBoolean("airport")));
	}

	private Location location(long id) {
		return locations().stream().filter(l -> l.id() == id).findFirst()
				.orElseThrow(() -> error(400, "Choose a valid pickup and destination."));
	}

	private RowMapper<Trip> mapper(boolean includePassengerPhone, boolean includeDriverContact) {
		Map<Long, Location> places = new HashMap<>();
		for (Location l : locations())
			places.put(l.id(), l);
		return (r, n) -> map(r, places, includePassengerPhone, includeDriverContact);
	}

	private Trip map(ResultSet r, Map<Long, Location> places, boolean includePassengerPhone, boolean includeDriverContact) throws SQLException {
		Number distance = (Number) r.getObject("distance_km");
		return new Trip(r.getLong("id"), r.getLong("passenger_id"), r.getString("passenger_name"),
				(Long) r.getObject("driver_id"), r.getString("driver_name"), r.getString("vehicle"),
				places.get(r.getLong("pickup_id")), places.get(r.getLong("destination_id")),
				r.getObject("via_id") == null ? null : places.get(r.getLong("via_id")), r.getString("flight"),
				r.getTimestamp("pickup_at").toInstant(), r.getInt("passengers"), r.getInt("bags"), r.getString("notes"),
				r.getString("dispatch_notes"), r.getString("status"), r.getInt("fare_cents"),
				distance == null ? null : distance.doubleValue(), r.getDouble("usd_xof_rate"), r.getLong("fare_xof"), r.getInt("version"),
				(Double) r.getObject("driver_lat"), (Double) r.getObject("driver_lng"),
				r.getTimestamp("location_at") == null ? null : r.getTimestamp("location_at").toInstant(),
				(Integer) r.getObject("eta_minutes"), includePassengerPhone ? r.getString("passenger_phone") : null,
				r.getTimestamp("arrived_at") == null ? null : r.getTimestamp("arrived_at").toInstant(),
				(Integer) r.getObject("wait_seconds"), r.getString("cancellation_reason"),
				r.getTimestamp("arrived_at") == null ? null : r.getTimestamp("arrived_at").toInstant().plus(Duration.ofMinutes(Math.max(1, waitGraceMinutes))),
				includeDriverContact ? r.getString("driver_phone") : null,
				includeDriverContact ? r.getString("driver_email") : null,
				r.getTimestamp("created_at").toInstant(),
				r.getTimestamp("updated_at").toInstant());
	}

	private static final String SELECT = "SELECT t.*,p.name passenger_name,p.phone passenger_phone,d.name driver_name,d.vehicle,d.phone driver_phone,d.email driver_email FROM trips t JOIN users p ON p.id=t.passenger_id LEFT JOIN users d ON d.id=t.driver_id ";

	public List<Trip> list(User u) {
		return switch (u.role()) {
		case "PASSENGER" -> db.query(SELECT + "WHERE t.passenger_id=? AND t.status NOT IN ('COMPLETED','CANCELLED') ORDER BY t.pickup_at DESC", mapper(false, true), u.id());
		case "DRIVER" -> db.query(SELECT + "WHERE t.driver_id=? AND t.status NOT IN ('COMPLETED','CANCELLED') ORDER BY t.pickup_at", mapper(true, false), u.id());
		default -> db.query(SELECT + "WHERE t.status NOT IN ('COMPLETED','CANCELLED') OR (t.status='COMPLETED' AND DATE(t.updated_at)=CURRENT_DATE) ORDER BY t.pickup_at", mapper(false, false));
		};
	}

	public HistoryPage dispatcherHistory(User user, String party, String search, String status,
			LocalDate from, LocalDate to, int page, int size) {
		role(user, "DISPATCHER");
		if (!Set.of("DRIVER", "PASSENGER").contains(party))
			throw error(400, "Choose a driver or passenger history.");
		return history(party, null, null, search, status, from, to, page, size);
	}

	public HistoryPage driverHistory(User user, String search, String status, LocalDate from, LocalDate to, int page, int size) {
		role(user, "DRIVER");
		return history("DRIVER_SELF", user.id(), null, search, status, from, to, page, size);
	}

	public HistoryPage passengerHistory(User user, String search, String status,
			LocalDate from, LocalDate to, int page, int size) {
		role(user, "PASSENGER");
		return history("PASSENGER_SELF", null, user.id(), search, status, from, to, page, size);
	}

	private HistoryPage history(String party, Long driverId, Long passengerId, String search, String status,
			LocalDate from, LocalDate to, int requestedPage, int requestedSize) {
		int page = Math.max(0, requestedPage);
		int size = Set.of(5, 10, 20).contains(requestedSize) ? requestedSize : 5;
		String tripStatus = status == null || status.isBlank() ? "COMPLETED" : status.toUpperCase(Locale.ROOT);
		if (!Set.of("COMPLETED", "CANCELLED", "ALL").contains(tripStatus))
			throw error(400, "Choose completed, cancelled, or all trips.");
		if (from != null && to != null && from.isAfter(to))
			throw error(400, "The start date must be on or before the end date.");

		StringBuilder where = new StringBuilder(" WHERE t.status IN ('COMPLETED','CANCELLED')");
		List<Object> args = new ArrayList<>();
		if (!tripStatus.equals("ALL")) {
			where.append(" AND t.status=?");
			args.add(tripStatus);
		}
		if (party != null) {
			where.append(party.equals("DRIVER") ? " AND t.driver_id IS NOT NULL" : "");
			if (party.equals("PASSENGER")) where.append(" AND t.passenger_id IS NOT NULL");
		}
		if (driverId != null) {
			where.append(" AND t.driver_id=?");
			args.add(driverId);
		}
		if (passengerId != null) {
			where.append(" AND t.passenger_id=?");
			args.add(passengerId);
		}
		if (from != null) {
			where.append(" AND t.pickup_at>=?");
			args.add(Timestamp.valueOf(from.atStartOfDay()));
		}
		if (to != null) {
			where.append(" AND t.pickup_at<?");
			args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
		}
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		if (!q.isEmpty()) {
			boolean searchDriver = party.equals("DRIVER") || party.equals("PASSENGER_SELF");
			String contactSearch = searchDriver
					? "LOWER(COALESCE(d.name,'')) LIKE ? OR LOWER(COALESCE(d.phone,'')) LIKE ? OR LOWER(COALESCE(d.email,'')) LIKE ?"
					: "LOWER(COALESCE(p.name,'')) LIKE ? OR LOWER(COALESCE(p.phone,'')) LIKE ? OR LOWER(COALESCE(p.email,'')) LIKE ?";
			where.append(" AND (" + contactSearch + " OR LOWER(COALESCE(pl.name,'')) LIKE ? OR LOWER(COALESCE(dl.name,'')) LIKE ? OR LOWER(COALESCE(t.flight,'')) LIKE ? OR CAST(t.id AS CHAR) LIKE ?)");
			String like = "%" + q + "%";
			args.add(like); args.add(like); args.add(like); args.add(like); args.add(like); args.add(like); args.add(like);
		}
		String fromSql = " FROM trips t JOIN users p ON p.id=t.passenger_id LEFT JOIN users d ON d.id=t.driver_id JOIN locations pl ON pl.id=t.pickup_id JOIN locations dl ON dl.id=t.destination_id" + where;
		long total = db.queryForObject("SELECT COUNT(*)" + fromSql, Long.class, args.toArray());
		long offset = (long) page * size;
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size); pageArgs.add(offset);
		var tripMapper = mapper(false, false);
		List<HistoryTrip> content = db.query("SELECT t.*,p.name passenger_name,d.name driver_name,d.vehicle,p.email passenger_email,p.phone passenger_phone,d.email driver_email,d.phone driver_phone,d.address driver_address" + fromSql + " ORDER BY t.pickup_at DESC,t.id DESC LIMIT ? OFFSET ?",
				(r, n) -> new HistoryTrip(tripMapper.mapRow(r, n), party.equals("DRIVER_SELF") ? null : r.getString("passenger_email"), party.equals("DRIVER_SELF") ? null : r.getString("passenger_phone"), party.equals("PASSENGER_SELF") ? null : r.getString("driver_email"), party.equals("PASSENGER_SELF") ? null : r.getString("driver_phone"), party.endsWith("SELF") ? null : r.getString("driver_address")), pageArgs.toArray());
		return new HistoryPage(content, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size));
	}

	public Trip get(long id, User u) {
		var rows = db.query(SELECT + "WHERE t.id=?", mapper(u.role().equals("DRIVER"), u.role().equals("PASSENGER")), id);
		if (rows.isEmpty())
			throw error(404, "Trip not found.");
		Trip t = rows.get(0);
		if (u.role().equals("PASSENGER") && u.id() != t.passengerId()
				|| u.role().equals("DRIVER") && !Objects.equals(t.driverId(), u.id()))
			throw error(404, "Trip not found.");
		return t;
	}

	private Trip lock(long id, User u, int version) {
		db.queryForList("SELECT id FROM trips WHERE id=? FOR UPDATE", id);
		Trip t = get(id, u);
		if (t.version() != version)
			throw error(409, "This trip changed. Refresh and try again.");
		return t;
	}

	private void event(long id, User u, String status, String detail) {
		db.update("INSERT INTO trip_events(trip_id,actor_id,status,detail) VALUES(?,?,?,?)", id, u.id(), status,
				detail);
	}

	private void routeValid(long p, long d, Long via) {
		Location pickup = location(p), destination = location(d);
		if (p == d || pickup.airport() == destination.airport())
			throw error(400, "Choose one airport terminal and one city pickup.");
		if (via != null) {
			location(via);
			if (via == p || via == d)
				throw error(400, "The extra stop must differ from pickup and destination.");
		}
	}

	public FareService.Quote fareQuote(User user, long pickupId, long destinationId) {
		role(user, "PASSENGER");
		routeValid(pickupId, destinationId, null);
		return fares.quote(location(pickupId), location(destinationId));
	}

	@Transactional
	public Trip book(User u, Booking b) {
		role(u, "PASSENGER");
		// Serialize same-passenger retries before checking the idempotency key.
		db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", u.id());
		var existing = db.queryForList("SELECT id FROM trips WHERE passenger_id=? AND request_key=?", u.id(),
				b.requestKey());
		if (!existing.isEmpty())
			return get(((Number) existing.get(0).get("id")).longValue(), u);
		routeValid(b.pickupId(), b.destinationId(), null);
		FareService.Quote quote = fares.quote(location(b.pickupId()), location(b.destinationId()));
		if (b.pickupAt().isBefore(Instant.now().plusSeconds(60))
				|| b.pickupAt().isAfter(Instant.now().plus(Duration.ofDays(180))))
			throw error(400, "Pickup must be at least a minute from now and within 180 days.");
		db.update(
				"INSERT INTO trips(passenger_id,pickup_id,destination_id,flight,pickup_at,passengers,bags,notes,fare_cents,distance_km,usd_xof_rate,fare_xof,request_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
				u.id(), b.pickupId(), b.destinationId(), b.flight().trim().toUpperCase(Locale.ROOT),
				Timestamp.from(b.pickupAt()), b.passengers(), b.bags(), b.notes(), quote.usdCents(), quote.distanceKm(),
				quote.usdToXof(), quote.xofAmount(),
				b.requestKey());
		long id = db.queryForObject("SELECT id FROM trips WHERE passenger_id=? AND request_key=?", Long.class, u.id(),
				b.requestKey());
		event(id, u, "REQUESTED", "Ride requested");
		return get(id, u);
	}

	@Transactional
	public Trip assign(long id, User u, Assign a) {
		role(u, "DISPATCHER");
		Trip t = lock(id, u, a.version());
		if (!Set.of("REQUESTED", "ASSIGNED").contains(t.status()))
			throw error(409, "A ride cannot be reassigned after the driver accepts it. The driver must release it back to dispatch first.");
		var drivers = db.queryForList("SELECT u.*,v.seatbelts AS vehicle_capacity FROM users u LEFT JOIN vehicles v ON v.id=u.vehicle_id AND v.active=true WHERE u.id=? AND u.role='DRIVER' AND u.active=true FOR UPDATE",
				a.driverId());
		if (drivers.isEmpty())
			throw error(400, "Choose a driver.");
		var d = drivers.get(0);
		Object rawCapacity = d.get("vehicle_capacity");
		int vehicleCapacity = rawCapacity instanceof Number n ? n.intValue() : 0;
		if (vehicleCapacity < t.passengers())
			throw error(409, "This driver needs an active vehicle with at least " + t.passengers() + " seatbelts.");
		int conflicts = db.queryForObject(
				"SELECT COUNT(*) FROM trips WHERE driver_id=? AND id<>? AND status NOT IN ('COMPLETED','CANCELLED') AND ABS(TIMESTAMPDIFF(MINUTE,pickup_at,?))<90",
				Integer.class, a.driverId(), id, Timestamp.from(t.pickupAt()));
		if (conflicts > 0)
			throw error(409, "Driver has another trip within 90 minutes of this pickup.");
		db.update(
			"UPDATE trips SET driver_id=?,status='ASSIGNED',driver_lat=NULL,driver_lng=NULL,location_at=NULL,eta_minutes=NULL,arrived_at=NULL,wait_seconds=NULL,cancellation_reason=NULL,alert_generation=alert_generation+1,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
				a.driverId(), id);
		db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL", id);
		event(id, u, "ASSIGNED", "Assigned to " + d.get("name"));
		return get(id, u);
	}

	public List<AssignmentOption> assignmentOptions(long tripId, User user) {
		role(user, "DISPATCHER");
		Trip target = get(tripId, user);
		if (!Set.of("REQUESTED", "ASSIGNED").contains(target.status()))
			throw error(409, "Assignment options are available only before a driver accepts the ride.");
		return db.query("SELECT u.id,u.name,u.vehicle,v.seatbelts vehicle_capacity,COUNT(t.id) conflict_count,MIN(t.pickup_at) next_conflict "
				+ "FROM users u LEFT JOIN vehicles v ON v.id=u.vehicle_id AND v.active=true "
				+ "LEFT JOIN trips t ON t.driver_id=u.id AND t.id<>? "
				+ "AND t.status NOT IN ('COMPLETED','CANCELLED') AND ABS(TIMESTAMPDIFF(MINUTE,t.pickup_at,?))<90 "
				+ "WHERE u.role='DRIVER' AND u.active=true GROUP BY u.id,u.name,u.vehicle,v.seatbelts ORDER BY u.name",
				(r, n) -> {
					int conflicts = r.getInt("conflict_count");
					Timestamp next = r.getTimestamp("next_conflict");
					int capacity = r.getInt("vehicle_capacity");
					boolean hasVehicle = !r.wasNull() && capacity > 0;
					boolean capacityAvailable = hasVehicle && capacity >= target.passengers();
					return new AssignmentOption(r.getLong("id"), r.getString("name"), r.getString("vehicle"),
							conflicts == 0 && capacityAvailable, capacityAvailable, capacity, target.passengers(),
							conflicts, next == null ? null : next.toInstant());
				}, tripId, Timestamp.from(target.pickupAt()));
	}

	@Transactional
	public Trip status(long id, User u, Status s) {
		Trip t = lock(id, u, s.version());
		if (s.status().equals("CANCELLED")) {
			role(u, "PASSENGER", "DISPATCHER");
			if (TERMINAL.contains(t.status()) || Set.of("BOARDING", "IN_TRANSIT").contains(t.status()))
				throw error(409, "This trip can no longer be cancelled.");
		} else if (s.status().equals("REQUESTED")) {
			role(u, "DRIVER");
			if (!Set.of("ASSIGNED", "ACCEPTED").contains(t.status()) || !Objects.equals(t.driverId(), u.id()))
				throw error(409, "Only your pending or accepted assignment can be released.");
		} else {
			role(u, "DRIVER");
			if (!s.status().equals(NEXT.get(t.status())))
				throw error(409, "This status change is not allowed.");
			if (s.status().equals("EN_ROUTE")) {
				db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", u.id());
				if (db.queryForObject(
						"SELECT COUNT(*) FROM trips WHERE driver_id=? AND id<>? AND status IN ('EN_ROUTE','ARRIVING','BOARDING','IN_TRANSIT')",
						Integer.class, u.id(), id) > 0)
					throw error(409, "Finish your active trip before starting another.");
			}
		}
		String cancellationReason = s.status().equals("CANCELLED")
				? (u.role().equals("PASSENGER") ? "PASSENGER_CANCELLED" : "DISPATCHER_CANCELLED") : null;
		db.update("UPDATE trips SET status=?,driver_id=?,arrived_at=CASE WHEN ?='ARRIVING' THEN COALESCE(arrived_at,CURRENT_TIMESTAMP) ELSE arrived_at END,"
				+ "wait_seconds=CASE WHEN ? IN ('BOARDING','CANCELLED') AND arrived_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND,arrived_at,CURRENT_TIMESTAMP) ELSE wait_seconds END,"
				+ "cancellation_reason=COALESCE(?,cancellation_reason),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
				s.status(), s.status().equals("REQUESTED") ? null : t.driverId(), s.status(), s.status(), cancellationReason, id);
		if (s.status().equals("ARRIVING"))
			db.update("INSERT IGNORE INTO notifications(user_id,trip_id,kind,message) VALUES(?,?, 'DRIVER_ARRIVED',?)",
					t.passengerId(), id, "Your driver has arrived at " + t.pickup().name() + ". The wait timer has started.");
		if (TERMINAL.contains(s.status()))
			db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL", id);
		String eventDetail = s.status().equals("REQUESTED")
				? (t.status().equals("ACCEPTED") ? "Driver released accepted ride back to dispatch" : "Driver declined assignment")
				: s.status().replace('_', ' ');
		event(id, u, s.status(), eventDetail);
		return s.status().equals("REQUESTED") ? null : get(id, u);
	}

	@Transactional
	public Trip reportNoShow(long id, User u, int version) {
		role(u, "DRIVER");
		Trip t = lock(id, u, version);
		if (!"ARRIVING".equals(t.status()) || !Objects.equals(t.driverId(), u.id()) || t.arrivedAt() == null)
			throw error(409, "A passenger no-show can be recorded only while you are waiting at pickup.");
		long waited = Math.max(0, Duration.between(t.arrivedAt(), Instant.now()).toSeconds());
		if (waited < Math.max(1, waitGraceMinutes) * 60L)
			throw error(409, "Wait at pickup for " + Math.max(1, waitGraceMinutes) + " minutes before reporting a no-show.");
		db.update("UPDATE trips SET status='CANCELLED',wait_seconds=?,cancellation_reason='PASSENGER_NO_SHOW',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
				Math.toIntExact(Math.min(waited, Integer.MAX_VALUE)), id);
		db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL", id);
		event(id, u, "CANCELLED", "Passenger no-show after " + (waited / 60) + " minutes at pickup");
		return get(id, u);
	}

	@Transactional
	public Trip route(long id, User u, Route r) {
		role(u, "DISPATCHER");
		Trip t = lock(id, u, r.version());
		if (TERMINAL.contains(t.status()))
			throw error(409, "Completed or cancelled routes cannot be edited.");
		if (Set.of("BOARDING", "IN_TRANSIT").contains(t.status()) && r.pickupId() != t.pickup().id())
			throw error(409, "Pickup cannot change after boarding starts.");
		routeValid(r.pickupId(), r.destinationId(), r.viaId());
		db.update(
				"UPDATE trips SET pickup_id=?,destination_id=?,via_id=?,dispatch_notes=?,eta_minutes=NULL,alert_generation=alert_generation+1,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
				r.pickupId(), r.destinationId(), r.viaId(), r.notes(), id);
		db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL", id);
		event(id, u, t.status(),
				"Route updated: " + location(r.pickupId()).name() + " to " + location(r.destinationId()).name()
						+ (r.viaId() == null ? "" : " via " + location(r.viaId()).name())
						+ (r.notes().isBlank() ? "" : ". " + r.notes()));
		return get(id, u);
	}

	@Transactional
	public Trip position(long id, User u, Position p) {
		role(u, "DRIVER");
		db.queryForList("SELECT id FROM trips WHERE id=? FOR UPDATE", id);
		Trip t = get(id, u);
		if (!Set.of("EN_ROUTE", "ARRIVING", "BOARDING", "IN_TRANSIT").contains(t.status()))
			throw error(409, "Start the trip before sharing location.");
		if (!Double.isFinite(p.lat()) || !Double.isFinite(p.lng()))
			throw error(400, "Coordinates must be finite.");
		db.update("UPDATE trips SET driver_lat=?,driver_lng=?,location_at=CURRENT_TIMESTAMP,eta_minutes=? WHERE id=?",
				p.lat(), p.lng(), p.etaMinutes(), id);
		if (p.etaMinutes() != null && p.etaMinutes() <= 5 && Set.of("EN_ROUTE", "ARRIVING").contains(t.status()))
			db.update(
					"INSERT IGNORE INTO notifications(user_id,trip_id,kind,message) SELECT ?,?,CONCAT('ARRIVING_SOON_',alert_generation),? FROM trips WHERE id=?",
					t.passengerId(), id,
					"Your shuttle is about " + p.etaMinutes() + " minutes away. Head to " + t.pickup().name() + ".",
					id);
		return get(id, u);
	}

	public List<Map<String, Object>> events(long id, User u) {
		get(id, u);
		return db.queryForList(
				"SELECT e.id,e.status,e.detail,e.created_at AS createdAt,u.name AS actor FROM trip_events e JOIN users u ON u.id=e.actor_id WHERE trip_id=? ORDER BY e.id",
				id);
	}

	public List<Map<String, Object>> drivers(User u) {
		role(u, "DISPATCHER");
		return db.queryForList(
				"SELECT u.id,u.name,u.email,u.phone,u.address,u.role,u.vehicle,u.seats,u.luggage_capacity AS luggageCapacity,u.vehicle_id AS vehicleId,CASE WHEN v.id IS NULL THEN NULL ELSE CONCAT(v.vehicle_year,' ',v.make,' ',v.model,' · ',v.color) END AS vehicleLabel,EXISTS(SELECT 1 FROM driver_documents dd WHERE dd.driver_id=u.id) AS hasLicenseDocument,(SELECT file_name FROM driver_documents dd WHERE dd.driver_id=u.id) AS licenseFileName,(SELECT content_type FROM driver_documents dd WHERE dd.driver_id=u.id) AS licenseContentType FROM users u LEFT JOIN vehicles v ON v.id=u.vehicle_id AND v.active=true WHERE u.role='DRIVER' AND u.active=true ORDER BY u.name");
	}
}
