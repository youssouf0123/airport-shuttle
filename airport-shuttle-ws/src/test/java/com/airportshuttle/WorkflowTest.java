package com.airportshuttle;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:3306/airport_shuttle_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true","shuttle.seed-demo=true"})
@AutoConfigureMockMvc
@Transactional
class WorkflowTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
 @Value("${shuttle.demo-password}") String password;
 MockHttpSession passenger,dispatch,driver,driver2;long driverId;
 @BeforeEach void sessions()throws Exception{
  for(String table:List.of("push_deliveries","push_subscriptions","notifications","trip_events","trips"))db.update("DELETE FROM "+table);
  passenger=login("passenger");dispatch=login("dispatch");driver=login("driver");driver2=login("driver2");driverId=((Auth.User)driver.getAttribute("user")).id();
 }
 MockHttpSession login(String name)throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/login").header("X-Shuttle-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("email",name+"@shuttle.local","password",password)))).andExpect(status().isOk()).andReturn().getRequest().getSession();}
 JsonNode call(String method,String path,MockHttpSession session,Object body,int expected)throws Exception{
  var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
  request.session(session).header("X-Shuttle-Request","1");if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
  var response=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();String text=response.getContentAsString();return text.isEmpty()?json.nullNode():json.readTree(text);
 }
 Map<String,Object> booking(){return new HashMap<>(Map.of("pickupId",4,"destinationId",1,"flight","DL 204","pickupAt",Instant.now().plusSeconds(3600).toString(),"passengers",2,"bags",2,"notes","Two blue suitcases","requestKey",UUID.randomUUID().toString()));}
 JsonNode book()throws Exception{return call("POST","/api/trips",passenger,booking(),200);}
 JsonNode assign(JsonNode trip)throws Exception{return call("PUT","/api/trips/"+trip.get("id").asLong()+"/assignment",dispatch,Map.of("driverId",driverId,"version",trip.get("version").asInt()),200);}
 JsonNode state(JsonNode trip,String state)throws Exception{return call("PUT","/api/trips/"+trip.get("id").asLong()+"/status",driver,Map.of("status",state,"version",trip.get("version").asInt()),200);}
 @Test void fullJourneyPersistsReceiptAndAudit()throws Exception{
  JsonNode t=assign(book());long id=t.get("id").asLong();
  for(String s:List.of("ACCEPTED","EN_ROUTE","ARRIVING","BOARDING","IN_TRANSIT","COMPLETED"))t=state(t,s);
  assertThat(call("GET","/api/trips/"+id,passenger,null,200).get("status").asText()).isEqualTo("COMPLETED");
  mvc.perform(get("/api/trips/"+id+"/receipt").session(passenger)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Payment has not been collected")));
  assertThat(call("GET","/api/trips/"+id+"/events",dispatch,null,200).size()).isEqualTo(8);
 }
 @Test void bookingsAreValidatedAndRetriesAreIdempotent()throws Exception{
  var b=booking();JsonNode first=call("POST","/api/trips",passenger,b,200);assertThat(call("POST","/api/trips",passenger,b,200).get("id")).isEqualTo(first.get("id"));
  b=booking();b.put("passengers",0);call("POST","/api/trips",passenger,b,400);
  b=booking();b.put("bags",13);call("POST","/api/trips",passenger,b,400);
  b=booking();b.put("destinationId",4);call("POST","/api/trips",passenger,b,400);
  b=booking();b.put("pickupAt",Instant.now().minusSeconds(60).toString());call("POST","/api/trips",passenger,b,400);
 }
 @Test void authenticationAndRolesAreEnforced()throws Exception{
  mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/trips").session(passenger).contentType("application/json").content(json.writeValueAsString(booking()))).andExpect(status().isForbidden());
  call("POST","/api/trips",driver,booking(),403);
  JsonNode t=book();long id=t.get("id").asLong();call("PUT","/api/trips/"+id+"/assignment",passenger,Map.of("driverId",driverId,"version",0),403);
  call("GET","/api/trips/"+id,driver2,null,404);call("GET","/api/drivers",passenger,null,403);
 }
 @Test void sameEmailCanHaveIndependentAccountsInDifferentWorkspaces()throws Exception{
  String email="shared-"+UUID.randomUUID()+"@example.com";
  String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("DispatcherPass123");
  db.update("INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,'PASSENGER')","Shared Person",email,hash);
  db.update("INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,'DISPATCHER')","Shared Person",email,hash);
  var passengerLogin=mvc.perform(post("/api/auth/login").header("X-Shuttle-Request","1").header("X-Shuttle-Role","PASSENGER").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","DispatcherPass123")))).andExpect(status().isOk()).andReturn();
  assertThat(json.readTree(passengerLogin.getResponse().getContentAsString()).get("role").asText()).isEqualTo("PASSENGER");
  var dispatcherLogin=mvc.perform(post("/api/auth/login").header("X-Shuttle-Request","1").header("X-Shuttle-Role","DISPATCHER").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","DispatcherPass123")))).andExpect(status().isOk()).andReturn();
  assertThat(json.readTree(dispatcherLogin.getResponse().getContentAsString()).get("role").asText()).isEqualTo("DISPATCHER");
 }
 @Test void passengerCannotReadAnotherPassengersTrip()throws Exception{
  JsonNode t=book();var request=post("/api/auth/register").header("X-Shuttle-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("name","Other Passenger","email",UUID.randomUUID()+"@example.com","password","AnotherLongPassword1","role","DISPATCHER")));
  var result=mvc.perform(request).andExpect(status().isOk()).andReturn();MockHttpSession other=(MockHttpSession)result.getRequest().getSession();
  assertThat(json.readTree(result.getResponse().getContentAsString()).get("role").asText()).isEqualTo("PASSENGER");
  call("GET","/api/trips/"+t.get("id").asLong(),other,null,404);
 }
 @Test void conflictsAndCapacityPreventDoubleAssignments()throws Exception{
  assign(book());JsonNode second=book();call("PUT","/api/trips/"+second.get("id").asLong()+"/assignment",dispatch,Map.of("driverId",driverId,"version",0),409);
  long other=db.queryForObject("SELECT id FROM users WHERE email='driver2@shuttle.local'",Long.class);db.update("UPDATE users SET seats=1 WHERE id=?",other);
  call("PUT","/api/trips/"+second.get("id").asLong()+"/assignment",dispatch,Map.of("driverId",other,"version",0),409);
 }
 @Test void staleVersionsAndInvalidTransitionsFail()throws Exception{
  JsonNode t=assign(book());long id=t.get("id").asLong();call("PUT","/api/trips/"+id+"/status",driver,Map.of("status","COMPLETED","version",1),409);
  call("PUT","/api/trips/"+id+"/assignment",dispatch,Map.of("driverId",driverId,"version",0),409);
  t=state(t,"ACCEPTED");t=state(t,"EN_ROUTE");t=state(t,"ARRIVING");t=state(t,"BOARDING");
  call("PUT","/api/trips/"+id+"/status",passenger,Map.of("status","CANCELLED","version",t.get("version").asInt()),409);
 }
 @Test void routeChangesAreAuditedAndDeclinesReturnToDispatch()throws Exception{
  JsonNode t=assign(book());long id=t.get("id").asLong();
  t=call("PUT","/api/trips/"+id+"/route",dispatch,Map.of("pickupId",5,"destinationId",2,"viaId",6,"notes","Use west entrance","version",1),200);
  assertThat(t.get("via").get("id").asLong()).isEqualTo(6);assertThat(t.get("dispatchNotes").asText()).isEqualTo("Use west entrance");
  state(t,"REQUESTED");JsonNode declined=call("GET","/api/trips/"+id,dispatch,null,200);assertThat(declined.get("driverId").isNull()).isTrue();assertThat(declined.get("status").asText()).isEqualTo("REQUESTED");call("GET","/api/trips/"+id,driver,null,404);
 }
 @Test void locationAndFiveMinuteAlertArePersistedOnce()throws Exception{
  JsonNode t=state(state(assign(book()),"ACCEPTED"),"EN_ROUTE");long id=t.get("id").asLong();
  for(int eta:List.of(9,5,4,3))call("PUT","/api/trips/"+id+"/location",driver,Map.of("lat",40.7,"lng",-73.9,"etaMinutes",eta),200);
  JsonNode latest=call("GET","/api/trips/"+id,passenger,null,200);assertThat(latest.get("driverLat").asDouble()).isEqualTo(40.7);assertThat(latest.get("etaMinutes").asInt()).isEqualTo(3);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE trip_id=?",Integer.class,id)).isEqualTo(1);
  call("PUT","/api/trips/"+id+"/location",driver2,Map.of("lat",40.7,"lng",-73.9,"etaMinutes",5),404);
  call("PUT","/api/trips/"+id+"/location",driver,Map.of("lat",999,"lng",-73.9,"etaMinutes",5),400);
 }
 @Test void cancellationAndReceiptAvailability()throws Exception{
  JsonNode t=book();long id=t.get("id").asLong();call("GET","/api/trips/"+id+"/receipt",passenger,null,409);
  call("PUT","/api/trips/"+id+"/status",passenger,Map.of("status","CANCELLED","version",0),200);
  call("PUT","/api/trips/"+id+"/assignment",dispatch,Map.of("driverId",driverId,"version",1),409);
 }
 @Test void reassignmentClearsStaleLocationAndRenewsArrivalAlerts()throws Exception{
  JsonNode t=state(state(assign(book()),"ACCEPTED"),"EN_ROUTE");long id=t.get("id").asLong();
  call("PUT","/api/trips/"+id+"/location",driver,Map.of("lat",40.7,"lng",-73.9,"etaMinutes",5),200);
  long other=((Auth.User)driver2.getAttribute("user")).id();t=call("PUT","/api/trips/"+id+"/assignment",dispatch,Map.of("driverId",other,"version",t.get("version").asInt()),200);
  assertThat(t.get("driverLat").isNull()).isTrue();assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE trip_id=? AND read_at IS NULL",Integer.class,id)).isZero();
  call("PUT","/api/trips/"+id+"/location",driver,Map.of("lat",40.7,"lng",-73.9,"etaMinutes",5),404);
  for(String s:List.of("ACCEPTED","EN_ROUTE"))t=call("PUT","/api/trips/"+id+"/status",driver2,Map.of("status",s,"version",t.get("version").asInt()),200);
  call("PUT","/api/trips/"+id+"/location",driver2,Map.of("lat",40.71,"lng",-73.9,"etaMinutes",4),200);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE trip_id=?",Integer.class,id)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE trip_id=? AND read_at IS NULL",Integer.class,id)).isEqualTo(1);
 }
 @Test void pushKeyAndSubscriptionValidation()throws Exception{
  assertThat(call("GET","/api/push/key",passenger,null,200).get("publicKey").asText()).hasSizeGreaterThan(60);
  call("POST","/api/push/subscribe",passenger,Map.of("endpoint","https://127.0.0.1/admin","keys",Map.of("p256dh","invalid","auth","invalid")),400);
  assertThatThrownBy(()->Push.validateEndpoint("https://fcm.googleapis.com.evil.test/push")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void dispatcherCanAssignAndUnassignDriverVehicles()throws Exception{
  JsonNode vehicle=call("POST","/api/vehicles",dispatch,Map.of("year",2024,"make","Toyota","model","Hiace","color","White","doors","4 doors","seatbelts",12,"wheelchairRamp",false),200);
  long vehicleId=vehicle.get("id").asLong();
  JsonNode assigned=call("POST","/api/vehicles/"+vehicleId+"/assignDriver/"+driverId,dispatch,null,200);
  assertThat(assigned.get("driverId").asLong()).isEqualTo(driverId);
  JsonNode drivers=call("GET","/api/drivers",dispatch,null,200);
  assertThat(drivers.findValue("vehicleId").asLong()).isEqualTo(vehicleId);
  JsonNode cleared=call("DELETE","/api/vehicles/"+vehicleId+"/driver",dispatch,null,200);
  assertThat(cleared.get("driverId").isNull()).isTrue();
 }
}
