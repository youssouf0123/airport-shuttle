package com.airportshuttle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.springframework.web.client.RestClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import static com.airportshuttle.Auth.*;
import static com.airportshuttle.TripService.*;

@RestController
@RequestMapping("/api")
public class Api {
	private final TripService trips;
	private final JdbcTemplate db;
	private final VehicleCatalogService vehicleCatalog;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final RestClient photon = RestClient.builder().baseUrl("https://photon.komoot.io")
			.defaultHeader("User-Agent", "AirportShuttle/1.0 contact@airport-shuttle.local").build();

	public Api(TripService trips, JdbcTemplate db, VehicleCatalogService vehicleCatalog) {
		this.trips = trips;
		this.db = db;
		this.vehicleCatalog = vehicleCatalog;
	}

	public record DriverCreate(@NotBlank @Size(max = 100) String name, @NotBlank @Email @Size(max = 190) String email,
			@NotBlank @Size(max = 30) String phone, @NotBlank @Size(min = 10, max = 72) String password,
			@Size(max = 100) String vehicle, @Size(max = 500) String address) {
	}

	public record DriverUpdate(@NotBlank @Size(max = 100) String name, @NotBlank @Email @Size(max = 190) String email,
			@NotBlank @Size(max = 30) String phone, @Size(max = 100) String vehicle,
			@Size(max = 500) String address) {
	}

	public record VehicleCreate(@Min(1990) @Max(2100) int year, @NotBlank @Size(max = 60) String make,
			@NotBlank @Size(max = 80) String model, @NotBlank @Size(max = 40) String color,
			@NotBlank @Size(max = 20) String doors, @Min(1) @Max(100) int seatbelts, boolean wheelchairRamp,
			@Size(max = 40) String licensePlate, @Size(max = 40) String vin, @Size(max = 120) String engineType,
			@Min(0) @Max(2000000) Integer mileage) {
	}

	@GetMapping("/health")
	public Map<String, String> health() {
		db.queryForObject("SELECT 1", Integer.class);
		return Map.of("status", "ok");
	}

	@GetMapping("/vehicle-reference")
	public Map<String, Object> vehicleReference(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) String make, @RequestParam(required = false) String model) {
		return Map.of("years", vehicleCatalog.years(), "makes", vehicleCatalog.makes(year), "models",
				year != null && make != null && !make.isBlank() ? vehicleCatalog.models(year, make) : List.of(),
				"engineTypes",
				year != null && make != null && model != null && !model.isBlank()
						? vehicleCatalog.engines(year, make, model)
						: List.of());
	}

	@GetMapping("/locations")
	public List<Location> locations() {
		return trips.locations();
	}

	@GetMapping("/places/search")
	public List<Map<String, Object>> searchPlaces(@RequestParam String q) {
		if (q == null || q.trim().length() < 2)
			return List.of();
		Map<?, ?> response = photon
				.get().uri(uri -> uri.path("/api").queryParam("q", q.trim()).queryParam("limit", 8)
						.queryParam("lang", "en").queryParam("bbox", "-8.15,12.45,-7.75,12.75").build())
				.retrieve().body(Map.class);
		if (response == null || !(response.get("features") instanceof List<?> features))
			return List.of();
		List<Map<String, Object>> results = new ArrayList<>();
		for (Object item : features) {
			if (!(item instanceof Map<?, ?> feature) || !(feature.get("geometry") instanceof Map<?, ?> geometry)
					|| !(geometry.get("coordinates") instanceof List<?> coordinates) || coordinates.size() < 2)
				continue;
			Map<?, ?> props = feature.get("properties") instanceof Map<?, ?> p ? p : Map.of();
			Object rawName = props.get("name");
			String name = rawName == null ? q.trim() : String.valueOf(rawName);
			String detail = String.join(", ", Stream.of(props.get("street"), props.get("city"), props.get("country"))
					.filter(Objects::nonNull).map(String::valueOf).filter(s -> !s.isBlank()).toList());
			results.add(Map.of("name", name, "detail", detail, "lat", ((Number) coordinates.get(1)).doubleValue(),
					"lng", ((Number) coordinates.get(0)).doubleValue(), "airport",
					name.toLowerCase(Locale.ROOT).contains("airport")
							|| name.toLowerCase(Locale.ROOT).contains("aéroport")));
		}
		return results;
	}

	@PostMapping("/locations/resolve")
	public Location resolve(@RequestBody Map<String, Object> input, HttpServletRequest r) {
		User u = user(r);
		String name = String.valueOf(input.getOrDefault("name", ""));
		String detail = String.valueOf(input.getOrDefault("detail", ""));
		double lat = ((Number) input.get("lat")).doubleValue(), lng = ((Number) input.get("lng")).doubleValue();
		boolean airport = Boolean.TRUE.equals(input.get("airport"));
		List<Location> existing = trips.locations();
		return existing.stream().filter(
				l -> l.name().equals(name) && Math.abs(l.lat() - lat) < .0001 && Math.abs(l.lng() - lng) < .0001)
				.findFirst().orElseGet(() -> {
					db.update("INSERT INTO locations(name,detail,lat,lng,airport) VALUES(?,?,?,?,?)", name, detail, lat,
							lng, airport);
					return trips.locations().stream().max(Comparator.comparingLong(Location::id)).orElseThrow();
				});
	}

	@GetMapping("/trips")
	public List<Trip> list(HttpServletRequest r) {
		return trips.list(user(r));
	}

	@GetMapping("/fares/quote")
	public FareService.Quote fareQuote(@RequestParam long pickupId, @RequestParam long destinationId,
			HttpServletRequest r) {
		return trips.fareQuote(user(r), pickupId, destinationId);
	}

	@GetMapping("/history/dispatcher")
	public HistoryPage dispatcherHistory(@RequestParam(defaultValue = "DRIVER") String party,
			@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "COMPLETED") String status,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
			HttpServletRequest r) {
		return trips.dispatcherHistory(user(r), party.toUpperCase(Locale.ROOT), search, status, from, to, page, size);
	}

	@GetMapping("/history/driver")
	public HistoryPage driverHistory(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "COMPLETED") String status,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
			HttpServletRequest r) {
		return trips.driverHistory(user(r), search, status, from, to, page, size);
	}

	@GetMapping("/history/passenger")
	public HistoryPage passengerHistory(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "COMPLETED") String status,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "5") int size,
			HttpServletRequest r) {
		return trips.passengerHistory(user(r), search, status, from, to, page, size);
	}

	@GetMapping("/trips/{id}")
	public Trip get(@PathVariable long id, HttpServletRequest r) {
		return trips.get(id, user(r));
	}

	@PutMapping(value = "/auth/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Boolean> uploadProfilePhoto(@RequestParam("file") MultipartFile file, HttpServletRequest r) throws java.io.IOException {
		Auth.User current = user(r);
		if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
			throw error(400, "Choose a profile photo up to 5 MB.");
		String contentType = file.getContentType();
		byte[] content = file.getBytes();
		if (!validProfilePhoto(contentType, content))
			throw error(400, "Profile photos must be JPEG, PNG, or WebP images.");
		db.update("INSERT INTO profile_photos(user_id,content_type,content,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP) "
				+ "ON DUPLICATE KEY UPDATE content_type=VALUES(content_type),content=VALUES(content),updated_at=CURRENT_TIMESTAMP",
				current.id(), contentType, content);
		return Map.of("saved", true);
	}

	@GetMapping("/auth/profile/photo")
	public ResponseEntity<byte[]> ownProfilePhoto(HttpServletRequest r) {
		return profilePhoto(user(r).id());
	}

	@DeleteMapping("/auth/profile/photo")
	public Map<String, Boolean> deleteProfilePhoto(HttpServletRequest r) {
		db.update("DELETE FROM profile_photos WHERE user_id=?", user(r).id());
		return Map.of("deleted", true);
	}

	@GetMapping("/trips/{id}/driver-photo")
	public ResponseEntity<byte[]> assignedDriverPhoto(@PathVariable long id, HttpServletRequest r) {
		Auth.User current = user(r);
		return profilePhoto(tripPhotoOwner(id, current, true));
	}

	@GetMapping("/trips/{id}/passenger-photo")
	public ResponseEntity<byte[]> assignedPassengerPhoto(@PathVariable long id, HttpServletRequest r) {
		Auth.User current = user(r);
		return profilePhoto(tripPhotoOwner(id, current, false));
	}

	private long tripPhotoOwner(long tripId, Auth.User current, boolean driverPhoto) {
		Map<String, Object> trip = db.query("SELECT passenger_id,driver_id,status FROM trips WHERE id=?", (rs, row) -> {
			Map<String, Object> result = new HashMap<>();
			result.put("passengerId", rs.getLong("passenger_id"));
			result.put("driverId", rs.getObject("driver_id"));
			result.put("status", rs.getString("status"));
			return result;
		}, tripId).stream().findFirst().orElseThrow(() -> error(404, "Trip not found."));
		String status = (String) trip.get("status");
		if (!List.of("ACCEPTED", "EN_ROUTE", "ARRIVING", "BOARDING", "IN_TRANSIT").contains(status))
			throw error(404, "Profile photos are available after the driver accepts the trip.");
		long passengerId = ((Number) trip.get("passengerId")).longValue();
		Object rawDriverId = trip.get("driverId");
		if (rawDriverId == null) throw error(404, "Trip has no assigned driver.");
		long driverId = ((Number) rawDriverId).longValue();
		if (driverPhoto && current.role().equals("PASSENGER") && current.id() == passengerId) return driverId;
		if (!driverPhoto && current.role().equals("DRIVER") && current.id() == driverId) return passengerId;
		throw error(403, "This trip photo is not available to your account.");
	}

	private boolean validProfilePhoto(String contentType, byte[] bytes) {
		if (contentType == null || bytes.length < 8) return false;
		return switch (contentType.toLowerCase(Locale.ROOT)) {
		case "image/jpeg" -> (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
		case "image/png" -> (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
		default -> false;
		};
	}

	private ResponseEntity<byte[]> profilePhoto(long userId) {
		Map<String, Object> photo = db.query("SELECT content_type,content FROM profile_photos WHERE user_id=?",
				(rs, row) -> Map.<String, Object>of("contentType", rs.getString("content_type"), "content", rs.getBytes("content")), userId)
				.stream().findFirst().orElseThrow(() -> error(404, "No profile photo uploaded."));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType((String) photo.get("contentType")));
		headers.setCacheControl(CacheControl.noCache().cachePrivate());
		return new ResponseEntity<>((byte[]) photo.get("content"), headers, HttpStatus.OK);
	}

	@PostMapping("/trips")
	public Trip book(@Valid @RequestBody Booking b, HttpServletRequest r) {
		return trips.book(user(r), b);
	}

	@PutMapping("/trips/{id}/assignment")
	public Trip assign(@PathVariable long id, @Valid @RequestBody Assign a, HttpServletRequest r) {
		return trips.assign(id, user(r), a);
	}

	@PutMapping("/trips/{id}/status")
	public Trip status(@PathVariable long id, @Valid @RequestBody Status s, HttpServletRequest r) {
		return trips.status(id, user(r), s);
	}

	@GetMapping("/trips/{id}/assignment-options")
	public List<AssignmentOption> assignmentOptions(@PathVariable long id, HttpServletRequest r) {
		return trips.assignmentOptions(id, user(r));
	}

	@PutMapping("/trips/{id}/no-show")
	public Trip reportNoShow(@PathVariable long id, @RequestBody Map<String, Integer> input, HttpServletRequest r) {
		Integer version = input.get("version");
		if (version == null || version < 0) throw error(400, "A current trip version is required.");
		return trips.reportNoShow(id, user(r), version);
	}

	@PutMapping("/trips/{id}/route")
	public Trip route(@PathVariable long id, @Valid @RequestBody Route route, HttpServletRequest r) {
		return trips.route(id, user(r), route);
	}

	@PutMapping("/trips/{id}/location")
	public Trip position(@PathVariable long id, @Valid @RequestBody Position p, HttpServletRequest r) {
		return trips.position(id, user(r), p);
	}

	@GetMapping("/trips/{id}/events")
	public List<Map<String, Object>> events(@PathVariable long id, HttpServletRequest r) {
		return trips.events(id, user(r));
	}

	@GetMapping("/drivers")
	public List<Map<String, Object>> drivers(HttpServletRequest r) {
		return trips.drivers(user(r));
	}

	@PostMapping("/drivers")
	public Map<String, Object> createDriver(@Valid @RequestBody DriverCreate input, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		String email = input.email().trim().toLowerCase(Locale.ROOT);
		try {
			db.update(
					"INSERT INTO users(name,email,phone,password_hash,role,vehicle,address) VALUES(?,?,?,?,'DRIVER',?,?)",
					input.name().trim(), email, input.phone().trim(), passwordEncoder.encode(input.password()),
					input.vehicle() == null ? "" : input.vehicle().trim(), blank(input.address()));
		} catch (org.springframework.dao.DuplicateKeyException ex) {
			throw error(409, "An account with this email already exists.");
		}
		long id = db.queryForObject("SELECT id FROM users WHERE email=? AND role='DRIVER'", Long.class, email);
		return driver(id);
	}

	@PostMapping(value = "/drivers/{id}/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Boolean> uploadDriverLicense(@PathVariable long id, @RequestParam("file") MultipartFile file,
			HttpServletRequest r) throws java.io.IOException {
		role(user(r), "DISPATCHER");
		ensureActiveDriver(id);
		if (file == null || file.isEmpty() || file.getSize() > 10L * 1024 * 1024)
			throw error(400, "Choose a driver license file up to 10 MB.");
		String contentType = file.getContentType();
		byte[] content = file.getBytes();
		if (!validLicenseFile(contentType, content))
			throw error(400, "Driver license must be a PDF, JPEG, or PNG file.");
		String originalName = file.getOriginalFilename() == null ? "driver-license" : file.getOriginalFilename();
		String fileName = originalName.replace('\\', '/');
		fileName = fileName.substring(fileName.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
		if (fileName.length() > 180) fileName = fileName.substring(fileName.length() - 180);
		db.update("INSERT INTO driver_documents(driver_id,file_name,content_type,file_size,content,updated_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP) "
				+ "ON DUPLICATE KEY UPDATE file_name=VALUES(file_name),content_type=VALUES(content_type),file_size=VALUES(file_size),content=VALUES(content),updated_at=CURRENT_TIMESTAMP",
				id, fileName, contentType, content.length, content);
		return Map.of("uploaded", true);
	}

	@GetMapping("/drivers/{id}/license")
	public ResponseEntity<byte[]> driverLicense(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		return driverLicenseResponse(id, false);
	}

	@GetMapping("/drivers/{id}/license/preview")
	public ResponseEntity<byte[]> driverLicensePreview(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		return driverLicenseResponse(id, true);
	}

	@GetMapping("/drivers/{id}/profile.pdf")
	public ResponseEntity<byte[]> driverProfilePdf(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		Map<String, Object> driver = activeDriverProfile(id);
		List<String> lines = new ArrayList<>(List.of("Name: " + driver.get("name"), "Email: " + driver.get("email"),
				"Phone: " + driver.get("phone"), "Address: " + driver.get("address"),
				"Vehicle: " + (driver.get("vehicleLabel") == null ? "Not assigned" : driver.get("vehicleLabel")),
				"Driver license: " + (driver.get("licenseFileName") != null ? driver.get("licenseFileName") : "Not uploaded")));
		return pdfResponse(ProfilePdf.create("DRIVER PROFILE", lines), "driver-" + id + "-profile.pdf");
	}

	@GetMapping("/vehicles")
	public List<Map<String, Object>> vehicles(HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		return db.queryForList(
				"SELECT v.id,v.vehicle_year AS year,v.make,v.model,v.color,v.doors,v.seatbelts,v.wheelchair_ramp AS wheelchairRamp,v.license_plate AS licensePlate,v.vin,v.engine_type AS engineType,v.mileage,v.active,u.id AS driverId,u.name AS driverName,u.phone AS driverPhone FROM vehicles v LEFT JOIN users u ON u.vehicle_id=v.id AND u.role='DRIVER' AND u.active=true WHERE v.active=true ORDER BY v.vehicle_year DESC,v.make,v.model");
	}

	@PostMapping("/vehicles")
	public Map<String, Object> createVehicle(@Valid @RequestBody VehicleCreate input, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		db.update(
				"INSERT INTO vehicles(vehicle_year,make,model,color,doors,seatbelts,wheelchair_ramp,license_plate,vin,engine_type,mileage) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
				input.year(), input.make().trim(), input.model().trim(), input.color().trim(), input.doors().trim(),
				input.seatbelts(), input.wheelchairRamp(), blank(input.licensePlate()), blank(input.vin()),
				blank(input.engineType()), input.mileage());
		long id = db.queryForObject("SELECT id FROM vehicles ORDER BY id DESC LIMIT 1", Long.class);
		return vehicle(id);
	}

	@PutMapping("/vehicles/{id}")
	public Map<String, Object> updateVehicle(@PathVariable long id, @Valid @RequestBody VehicleCreate input,
			HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		if (db.queryForObject("SELECT COUNT(*) FROM vehicles WHERE id=? AND active=true", Integer.class, id) == 0)
			throw error(404, "Vehicle not found.");
		db.update(
				"UPDATE vehicles SET vehicle_year=?,make=?,model=?,color=?,doors=?,seatbelts=?,wheelchair_ramp=?,license_plate=?,vin=?,engine_type=?,mileage=? WHERE id=? AND active=true",
				input.year(), input.make().trim(), input.model().trim(), input.color().trim(), input.doors().trim(),
				input.seatbelts(), input.wheelchairRamp(), blank(input.licensePlate()), blank(input.vin()),
				blank(input.engineType()), input.mileage(), id);
		return vehicle(id);
	}

	@GetMapping("/vehicles/{id}/profile.pdf")
	public ResponseEntity<byte[]> vehicleProfilePdf(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		Map<String, Object> vehicle = db.query("SELECT v.id,v.vehicle_year AS year,v.make,v.model,v.color,v.doors,v.seatbelts,v.wheelchair_ramp AS wheelchairRamp,v.license_plate AS licensePlate,v.vin,v.engine_type AS engineType,v.mileage,v.active,u.name AS driverName,u.phone AS driverPhone,u.email AS driverEmail "
				+ "FROM vehicles v LEFT JOIN users u ON u.vehicle_id=v.id AND u.role='DRIVER' AND u.active=true WHERE v.id=? AND v.active=true",
				(rs, row) -> {
					Map<String, Object> result = new LinkedHashMap<>();
					for (String column : List.of("id", "year", "make", "model", "color", "doors", "seatbelts", "wheelchairRamp", "licensePlate", "vin", "engineType", "mileage", "active", "driverName", "driverPhone", "driverEmail")) result.put(column, rs.getObject(column));
					return result;
				}, id).stream().findFirst().orElseThrow(() -> error(404, "Vehicle not found."));
		List<String> lines = new ArrayList<>(List.of("Year: " + vehicle.get("year"), "Make: " + vehicle.get("make"),
				"Model: " + vehicle.get("model"), "Engine type: " + (vehicle.get("engineType") == null ? "Not specified" : vehicle.get("engineType")),
				"Color: " + vehicle.get("color"), "Doors: " + vehicle.get("doors"), "Seatbelts: " + vehicle.get("seatbelts"),
				"Wheelchair ramp: " + Boolean.TRUE.equals(vehicle.get("wheelchairRamp")),
				"License plate: " + (vehicle.get("licensePlate") == null ? "Not specified" : vehicle.get("licensePlate")),
				"VIN: " + (vehicle.get("vin") == null ? "Not specified" : vehicle.get("vin")),
				"Mileage: " + (vehicle.get("mileage") == null ? "Not specified" : vehicle.get("mileage")),
				"Assigned driver: " + (vehicle.get("driverName") == null ? "Not assigned" : vehicle.get("driverName")),
				"Driver phone: " + (vehicle.get("driverPhone") == null ? "Not provided" : vehicle.get("driverPhone")),
				"Driver email: " + (vehicle.get("driverEmail") == null ? "Not provided" : vehicle.get("driverEmail"))));
		return pdfResponse(ProfilePdf.create("VEHICLE PROFILE", lines), "vehicle-" + id + "-profile.pdf");
	}

	private Map<String, Object> vehicle(long id) {
		return db.queryForMap(
				"SELECT v.id,v.vehicle_year AS year,v.make,v.model,v.color,v.doors,v.seatbelts,v.wheelchair_ramp AS wheelchairRamp,v.license_plate AS licensePlate,v.vin,v.engine_type AS engineType,v.mileage,v.active,u.id AS driverId,u.name AS driverName,u.phone AS driverPhone FROM vehicles v LEFT JOIN users u ON u.vehicle_id=v.id AND u.role='DRIVER' WHERE v.id=?",
				id);
	}

	private String blank(String value) {
		return value == null ? null : value.trim();
	}

	@PutMapping("/drivers/{id}")
	public Map<String, Object> updateDriver(@PathVariable long id, @Valid @RequestBody DriverUpdate input,
			HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='DRIVER' AND active=true", Integer.class,
				id) == 0)
			throw error(404, "Driver not found.");
		String email = input.email().trim().toLowerCase(Locale.ROOT);
		try {
			db.update(
					"UPDATE users SET name=?,email=?,phone=?,vehicle=?,address=? WHERE id=? AND role='DRIVER' AND active=true",
					input.name().trim(), email, input.phone().trim(),
				input.vehicle() == null ? "" : input.vehicle().trim(), blank(input.address()), id);
		} catch (org.springframework.dao.DuplicateKeyException ex) {
			throw error(409, "An account with this email already exists.");
		}
		return driver(id);
	}

	@DeleteMapping("/drivers/{id}")
	public Map<String, Boolean> deleteDriver(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='DRIVER' AND active=true", Integer.class,
				id) == 0)
			throw error(404, "Driver not found.");
		if (db.queryForObject(
				"SELECT COUNT(*) FROM trips WHERE driver_id=? AND status NOT IN ('COMPLETED','CANCELLED')",
				Integer.class, id) > 0)
			throw error(409, "This driver has an active trip and cannot be removed.");
		db.update("UPDATE users SET active=false,vehicle_id=NULL,vehicle='' WHERE id=? AND role='DRIVER'", id);
		return Map.of("ok", true);
	}

	private Map<String, Object> driver(long id) {
		return db.queryForMap(
				"SELECT u.id,u.name,u.email,u.phone,u.address,u.role,u.vehicle,u.seats,u.luggage_capacity AS luggageCapacity,u.vehicle_id AS vehicleId,CASE WHEN v.id IS NULL THEN NULL ELSE CONCAT(v.vehicle_year,' ',v.make,' ',v.model,' · ',v.color) END AS vehicleLabel,EXISTS(SELECT 1 FROM driver_documents dd WHERE dd.driver_id=u.id) AS hasLicenseDocument,(SELECT file_name FROM driver_documents dd WHERE dd.driver_id=u.id) AS licenseFileName,(SELECT content_type FROM driver_documents dd WHERE dd.driver_id=u.id) AS licenseContentType FROM users u LEFT JOIN vehicles v ON v.id=u.vehicle_id AND v.active=true WHERE u.id=? AND u.role='DRIVER' AND u.active=true",
				id);
	}

	private Map<String, Object> activeDriverProfile(long id) {
		if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='DRIVER' AND active=true", Integer.class, id) == 0)
			throw error(404, "Driver not found.");
		return driver(id);
	}

	private void ensureActiveDriver(long id) { activeDriverProfile(id); }

	private boolean validLicenseFile(String contentType, byte[] bytes) {
		if (contentType == null || bytes.length < 5) return false;
		return switch (contentType.toLowerCase(Locale.ROOT)) {
		case "application/pdf" -> bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
		case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
		case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		default -> false;
		};
	}

	private ResponseEntity<byte[]> driverLicenseResponse(long id, boolean inline) {
		ensureActiveDriver(id);
		Map<String, Object> document = db.query("SELECT file_name,content_type,content FROM driver_documents WHERE driver_id=?",
				(rs, row) -> Map.<String, Object>of("fileName", rs.getString("file_name"), "contentType", rs.getString("content_type"), "content", rs.getBytes("content")), id)
				.stream().findFirst().orElseThrow(() -> error(404, "Driver license not found."));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType((String) document.get("contentType")));
		headers.setContentDisposition(ContentDisposition.builder(inline ? "inline" : "attachment").filename((String) document.get("fileName")).build());
		return new ResponseEntity<>((byte[]) document.get("content"), headers, HttpStatus.OK);
	}

	private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
		return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
	}

	@DeleteMapping("/vehicles/{id}")
	public Map<String, Boolean> deleteVehicle(@PathVariable long id, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		if (db.queryForObject("SELECT COUNT(*) FROM vehicles WHERE id=? AND active=true", Integer.class, id) == 0)
			throw error(404, "Vehicle not found.");
		db.update("UPDATE users SET vehicle_id=NULL,vehicle='' WHERE vehicle_id=?", id);
		db.update("UPDATE vehicles SET active=false WHERE id=?", id);
		return Map.of("ok", true);
	}

	@PostMapping("/vehicles/{vehicleId}/assignDriver/{driverId}")
	public Map<String, Object> assignDriver(@PathVariable long vehicleId, @PathVariable long driverId,
			HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		if (db.queryForObject("SELECT COUNT(*) FROM vehicles WHERE id=? AND active=true", Integer.class,
				vehicleId) == 0)
			throw error(404, "Vehicle not found.");
		if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='DRIVER' AND active=true", Integer.class,
				driverId) == 0)
			throw error(404, "Driver not found.");
		db.update("UPDATE users SET vehicle_id=NULL,vehicle='' WHERE vehicle_id=?", vehicleId);
		db.update(
				"UPDATE users u JOIN vehicles v ON v.id=? SET u.vehicle_id=v.id,u.vehicle=CONCAT(v.vehicle_year,' ',v.make,' ',v.model,' · ',v.color) WHERE u.id=? AND u.role='DRIVER' AND u.active=true",
				vehicleId, driverId);
		return vehicle(vehicleId);
	}

	@DeleteMapping("/vehicles/{vehicleId}/driver")
	public Map<String, Object> unassignDriver(@PathVariable long vehicleId, HttpServletRequest r) {
		role(user(r), "DISPATCHER");
		db.update("UPDATE users SET vehicle_id=NULL,vehicle='' WHERE vehicle_id=?", vehicleId);
		return vehicle(vehicleId);
	}

	@GetMapping("/notifications")
	public List<Map<String, Object>> notifications(HttpServletRequest r) {
		return db.queryForList(
				"SELECT id,trip_id AS tripId,message,created_at AS createdAt,read_at AS readAt FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 100",
				user(r).id());
	}

	@PutMapping("/notifications/{id}/read")
	public Map<String, Boolean> read(@PathVariable long id, HttpServletRequest r) {
		db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", id, user(r).id());
		return Map.of("ok", true);
	}

	@GetMapping("/trips/{id}/receipt")
	public ResponseEntity<byte[]> receipt(@PathVariable long id, @RequestParam(defaultValue = "en") String language,
			HttpServletRequest r) {
		User passenger = user(r);
		role(passenger, "PASSENGER");
		Trip t = trips.get(id, passenger);
		if (!t.status().equals("COMPLETED"))
			throw error(409, "Receipts are available after completion.");
		boolean fr = "fr".equalsIgnoreCase(language);
		byte[] pdf = ReceiptPdf.create(t, fr);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=airport-shuttle-" + id + "-receipt.pdf")
				.contentType(MediaType.APPLICATION_PDF).body(pdf);
	}
}
