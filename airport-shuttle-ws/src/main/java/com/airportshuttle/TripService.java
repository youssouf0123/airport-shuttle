package com.airportshuttle;

import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.airportshuttle.Auth.*;

@Service
public class TripService {
 public record Location(long id,String name,String detail,double lat,double lng,boolean airport) {}
 public record Trip(long id,long passengerId,String passengerName,Long driverId,String driverName,String vehicle,
  Location pickup,Location destination,Location via,String flight,Instant pickupAt,int passengers,int bags,String notes,String dispatchNotes,
  String status,int fareCents,int version,Double driverLat,Double driverLng,Instant locationAt,Integer etaMinutes,Instant createdAt,Instant updatedAt) {}
 public record Booking(@Min(1) long pickupId,@Min(1) long destinationId,@NotBlank @Size(max=20) String flight,@NotNull Instant pickupAt,
  @Min(1) @Max(8) int passengers,@Min(0) @Max(12) int bags,@NotNull @Size(max=500) String notes,@NotBlank @Size(max=80) String requestKey) {}
 public record Assign(@Min(1) long driverId,@Min(0) int version) {}
 public record Status(@NotBlank String status,@Min(0) int version) {}
 public record Route(@Min(1) long pickupId,@Min(1) long destinationId,Long viaId,@NotNull @Size(max=500) String notes,@Min(0) int version) {}
 public record Position(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
  @Min(0) @Max(240) Integer etaMinutes) {}
 private final JdbcTemplate db;
 private static final Set<String> TERMINAL=Set.of("COMPLETED","CANCELLED");
 private static final Map<String,String> NEXT=Map.of("ASSIGNED","ACCEPTED","ACCEPTED","EN_ROUTE","EN_ROUTE","ARRIVING","ARRIVING","BOARDING","BOARDING","IN_TRANSIT","IN_TRANSIT","COMPLETED");
 public TripService(JdbcTemplate db) {this.db=db;}
 public List<Location> locations() {return db.query("SELECT * FROM locations ORDER BY id",(r,n)->new Location(r.getLong("id"),r.getString("name"),r.getString("detail"),r.getDouble("lat"),r.getDouble("lng"),r.getBoolean("airport")));}
 private Location location(long id) {return locations().stream().filter(l->l.id()==id).findFirst().orElseThrow(()->error(400,"Choose a valid pickup and destination."));}
 private RowMapper<Trip> mapper() {
  Map<Long,Location> places=new HashMap<>();for(Location l:locations())places.put(l.id(),l);
  return (r,n)->map(r,places);
 }
 private Trip map(ResultSet r,Map<Long,Location> places)throws SQLException {
  return new Trip(r.getLong("id"),r.getLong("passenger_id"),r.getString("passenger_name"),(Long)r.getObject("driver_id"),r.getString("driver_name"),r.getString("vehicle"),
   places.get(r.getLong("pickup_id")),places.get(r.getLong("destination_id")),r.getObject("via_id")==null?null:places.get(r.getLong("via_id")),r.getString("flight"),r.getTimestamp("pickup_at").toInstant(),r.getInt("passengers"),r.getInt("bags"),r.getString("notes"),r.getString("dispatch_notes"),r.getString("status"),r.getInt("fare_cents"),r.getInt("version"),(Double)r.getObject("driver_lat"),(Double)r.getObject("driver_lng"),r.getTimestamp("location_at")==null?null:r.getTimestamp("location_at").toInstant(),(Integer)r.getObject("eta_minutes"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
 }
 private static final String SELECT="SELECT t.*,p.name passenger_name,d.name driver_name,d.vehicle FROM trips t JOIN users p ON p.id=t.passenger_id LEFT JOIN users d ON d.id=t.driver_id ";
 public List<Trip> list(User u) {
  return switch(u.role()) {
   case "PASSENGER" -> db.query(SELECT+"WHERE t.passenger_id=? ORDER BY t.pickup_at DESC",mapper(),u.id());
   case "DRIVER" -> db.query(SELECT+"WHERE t.driver_id=? ORDER BY t.pickup_at",mapper(),u.id());
   default -> db.query(SELECT+"ORDER BY t.pickup_at",mapper());
  };
 }
 public Trip get(long id,User u) {
  var rows=db.query(SELECT+"WHERE t.id=?",mapper(),id);
  if(rows.isEmpty())throw error(404,"Trip not found.");
  Trip t=rows.get(0);
  if(u.role().equals("PASSENGER")&&u.id()!=t.passengerId() || u.role().equals("DRIVER")&&!Objects.equals(t.driverId(),u.id()))throw error(404,"Trip not found.");
  return t;
 }
 private Trip lock(long id,User u,int version) {
  db.queryForList("SELECT id FROM trips WHERE id=? FOR UPDATE",id);
  Trip t=get(id,u);
  if(t.version()!=version)throw error(409,"This trip changed. Refresh and try again.");
  return t;
 }
 private void event(long id,User u,String status,String detail) {db.update("INSERT INTO trip_events(trip_id,actor_id,status,detail) VALUES(?,?,?,?)",id,u.id(),status,detail);}
 private void routeValid(long p,long d,Long via) {
  Location pickup=location(p),destination=location(d);
  if(p==d || pickup.airport()==destination.airport())throw error(400,"Choose one airport terminal and one city pickup.");
  if(via!=null){location(via);if(via==p||via==d)throw error(400,"The extra stop must differ from pickup and destination.");}
 }
 @Transactional public Trip book(User u,Booking b) {
  role(u,"PASSENGER");
  // Serialize same-passenger retries before checking the idempotency key.
  db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",u.id());
  var existing=db.queryForList("SELECT id FROM trips WHERE passenger_id=? AND request_key=?",u.id(),b.requestKey());
  if(!existing.isEmpty())return get(((Number)existing.get(0).get("id")).longValue(),u);
  routeValid(b.pickupId(),b.destinationId(),null);
  if(b.pickupAt().isBefore(Instant.now().plusSeconds(60))||b.pickupAt().isAfter(Instant.now().plus(Duration.ofDays(180))))throw error(400,"Pickup must be at least a minute from now and within 180 days.");
  db.update("INSERT INTO trips(passenger_id,pickup_id,destination_id,flight,pickup_at,passengers,bags,notes,fare_cents,request_key) VALUES(?,?,?,?,?,?,?,?,?,?)",
   u.id(),b.pickupId(),b.destinationId(),b.flight().trim().toUpperCase(Locale.ROOT),Timestamp.from(b.pickupAt()),b.passengers(),b.bags(),b.notes(),4500+(b.passengers()-1)*800,b.requestKey());
  long id=db.queryForObject("SELECT id FROM trips WHERE passenger_id=? AND request_key=?",Long.class,u.id(),b.requestKey());
  event(id,u,"REQUESTED","Ride requested");return get(id,u);
 }
 @Transactional public Trip assign(long id,User u,Assign a) {
  role(u,"DISPATCHER");Trip t=lock(id,u,a.version());
  if(TERMINAL.contains(t.status())||Set.of("BOARDING","IN_TRANSIT").contains(t.status()))throw error(409,"This trip can no longer be reassigned.");
  var drivers=db.queryForList("SELECT * FROM users WHERE id=? AND role='DRIVER' AND active=true FOR UPDATE",a.driverId());
  if(drivers.isEmpty())throw error(400,"Choose a driver.");
  var d=drivers.get(0);
  if(t.passengers()>((Number)d.get("seats")).intValue()||t.bags()>((Number)d.get("luggage_capacity")).intValue())throw error(409,"This vehicle does not have enough capacity.");
  int conflicts=db.queryForObject("SELECT COUNT(*) FROM trips WHERE driver_id=? AND id<>? AND status NOT IN ('COMPLETED','CANCELLED') AND ABS(TIMESTAMPDIFF(MINUTE,pickup_at,?))<90",Integer.class,a.driverId(),id,Timestamp.from(t.pickupAt()));
  if(conflicts>0)throw error(409,"Driver has another trip within 90 minutes of this pickup.");
  db.update("UPDATE trips SET driver_id=?,status='ASSIGNED',driver_lat=NULL,driver_lng=NULL,location_at=NULL,eta_minutes=NULL,alert_generation=alert_generation+1,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",a.driverId(),id);
  db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL",id);
  event(id,u,"ASSIGNED","Assigned to "+d.get("name"));return get(id,u);
 }
 @Transactional public Trip status(long id,User u,Status s) {
  Trip t=lock(id,u,s.version());
  if(s.status().equals("CANCELLED")) {
   role(u,"PASSENGER","DISPATCHER");
   if(TERMINAL.contains(t.status())||Set.of("BOARDING","IN_TRANSIT").contains(t.status()))throw error(409,"This trip can no longer be cancelled.");
  } else if(s.status().equals("REQUESTED")) {
   role(u,"DRIVER");if(!t.status().equals("ASSIGNED"))throw error(409,"Only a pending assignment can be declined.");
  } else {
   role(u,"DRIVER");if(!s.status().equals(NEXT.get(t.status())))throw error(409,"This status change is not allowed.");
   if(s.status().equals("EN_ROUTE")) {
    db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",u.id());
    if(db.queryForObject("SELECT COUNT(*) FROM trips WHERE driver_id=? AND id<>? AND status IN ('EN_ROUTE','ARRIVING','BOARDING','IN_TRANSIT')",Integer.class,u.id(),id)>0)throw error(409,"Finish your active trip before starting another.");
   }
  }
  db.update("UPDATE trips SET status=?,driver_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",s.status(),s.status().equals("REQUESTED")?null:t.driverId(),id);
  if(TERMINAL.contains(s.status()))db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL",id);
  event(id,u,s.status(),s.status().equals("REQUESTED")?"Driver declined assignment":s.status().replace('_',' '));
  return s.status().equals("REQUESTED")?null:get(id,u);
 }
 @Transactional public Trip route(long id,User u,Route r) {
  role(u,"DISPATCHER");Trip t=lock(id,u,r.version());if(TERMINAL.contains(t.status()))throw error(409,"Completed or cancelled routes cannot be edited.");
  if(Set.of("BOARDING","IN_TRANSIT").contains(t.status())&&r.pickupId()!=t.pickup().id())throw error(409,"Pickup cannot change after boarding starts.");
  routeValid(r.pickupId(),r.destinationId(),r.viaId());
  db.update("UPDATE trips SET pickup_id=?,destination_id=?,via_id=?,dispatch_notes=?,eta_minutes=NULL,alert_generation=alert_generation+1,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",r.pickupId(),r.destinationId(),r.viaId(),r.notes(),id);
  db.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE trip_id=? AND read_at IS NULL",id);
  event(id,u,t.status(),"Route updated: "+location(r.pickupId()).name()+" to "+location(r.destinationId()).name()+(r.viaId()==null?"":" via "+location(r.viaId()).name())+(r.notes().isBlank()?"":". "+r.notes()));return get(id,u);
 }
 @Transactional public Trip position(long id,User u,Position p) {
  role(u,"DRIVER");db.queryForList("SELECT id FROM trips WHERE id=? FOR UPDATE",id);Trip t=get(id,u);
  if(!Set.of("EN_ROUTE","ARRIVING","BOARDING","IN_TRANSIT").contains(t.status()))throw error(409,"Start the trip before sharing location.");
  if(!Double.isFinite(p.lat())||!Double.isFinite(p.lng()))throw error(400,"Coordinates must be finite.");
  db.update("UPDATE trips SET driver_lat=?,driver_lng=?,location_at=CURRENT_TIMESTAMP,eta_minutes=? WHERE id=?",p.lat(),p.lng(),p.etaMinutes(),id);
  if(p.etaMinutes()!=null&&p.etaMinutes()<=5&&Set.of("EN_ROUTE","ARRIVING").contains(t.status()))
   db.update("INSERT IGNORE INTO notifications(user_id,trip_id,kind,message) SELECT ?,?,CONCAT('ARRIVING_SOON_',alert_generation),? FROM trips WHERE id=?",t.passengerId(),id,"Your shuttle is about "+p.etaMinutes()+" minutes away. Head to "+t.pickup().name()+".",id);
  return get(id,u);
 }
 public List<Map<String,Object>> events(long id,User u) {get(id,u);return db.queryForList("SELECT e.id,e.status,e.detail,e.created_at AS createdAt,u.name AS actor FROM trip_events e JOIN users u ON u.id=e.actor_id WHERE trip_id=? ORDER BY e.id",id);}
 public List<Map<String,Object>> drivers(User u) {role(u,"DISPATCHER");return db.queryForList("SELECT u.id,u.name,u.email,u.phone,u.role,u.vehicle,u.seats,u.luggage_capacity AS luggageCapacity,u.vehicle_id AS vehicleId,CASE WHEN v.id IS NULL THEN NULL ELSE CONCAT(v.vehicle_year,' ',v.make,' ',v.model,' · ',v.color) END AS vehicleLabel FROM users u LEFT JOIN vehicles v ON v.id=u.vehicle_id AND v.active=true WHERE u.role='DRIVER' AND u.active=true ORDER BY u.name");}
}
