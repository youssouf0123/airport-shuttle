package com.airportshuttle;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class Auth {
 public record User(long id,String name,String email,String phone,String role,String vehicle,int seats,int luggageCapacity) {}
 public record Login(@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(max=72) String password) {}
 public record Register(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=190) String email,@Size(max=30) String phone,@NotBlank @Size(min=10,max=72) String password) {}
 public record ProfileUpdate(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(max=30) String phone,String password) {}
 private final JdbcTemplate db;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 public Auth(JdbcTemplate db) {this.db=db;}
 public static User user(HttpServletRequest request) {
  var session=request.getSession(false);
  String workspace=request.getHeader("X-Shuttle-Role");
  if(workspace==null && request.getMethod().equals("GET"))workspace=request.getParameter("workspace");
  String key=workspace==null?"user":"user:"+workspace;
  if(session==null || !(session.getAttribute(key) instanceof User)) throw error(401,"Please sign in.");
  return (User)session.getAttribute(key);
 }
 public static void role(User user,String... roles) {
  if(!Arrays.asList(roles).contains(user.role())) throw error(403,"This action is not available for your account.");
 }
 public static ResponseStatusException error(int status,String message) {return new ResponseStatusException(HttpStatus.valueOf(status),message);}
 User find(long id) {return db.queryForObject("SELECT * FROM users WHERE id=?",(r,n)->new User(r.getLong("id"),r.getString("name"),r.getString("email"),r.getString("phone"),r.getString("role"),r.getString("vehicle"),r.getInt("seats"),r.getInt("luggage_capacity")),id);}
 @PostMapping("/login") public User login(@Valid @RequestBody Login input,HttpServletRequest request) {
  var rows=db.queryForList("SELECT id,password_hash FROM users WHERE email=? AND active=true",input.email().trim().toLowerCase(Locale.ROOT));
  String hash=rows.isEmpty()?"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy":(String)rows.getFirst().get("password_hash");
  if(!encoder.matches(input.password(),hash)||rows.isEmpty()) throw error(401,"Email or password is incorrect.");
  User user=find(((Number)rows.getFirst().get("id")).longValue());
  if(request.getHeader("X-Shuttle-Role")!=null&&!user.role().equals(request.getHeader("X-Shuttle-Role")))throw error(403,"This account belongs to the "+user.role().toLowerCase(Locale.ROOT)+" workspace. Use the matching link below.");
  establish(request,user);return user;
 }
 @PostMapping("/register") public User register(@Valid @RequestBody Register input,HttpServletRequest request) {
  String email=input.email().trim().toLowerCase(Locale.ROOT);
  try {db.update("INSERT INTO users(name,email,phone,password_hash,role) VALUES(?,?,?,?,'PASSENGER')",input.name().trim(),email,input.phone()==null?null:input.phone().trim(),encoder.encode(input.password()));}
  catch(org.springframework.dao.DuplicateKeyException ex) {throw error(409,"An account with this email already exists.");}
  User user=find(db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email));establish(request,user);return user;
 }
 private void establish(HttpServletRequest request,User user) {
  var session=request.getSession(true);request.changeSessionId();
  session.setAttribute("user:"+user.role(),user);
  session.setAttribute("user",user);
 }
 @GetMapping("/me") public User me(HttpServletRequest request) {return user(request);}
 @PutMapping("/profile") public User profile(@Valid @RequestBody ProfileUpdate input,HttpServletRequest request) {
  User current=user(request);String name=input.name().trim(),email=input.email().trim().toLowerCase(Locale.ROOT);
  if(input.password()!=null&&!input.password().isBlank()&&(input.password().length()<10||input.password().length()>72))throw error(400,"Password must be between 10 and 72 characters.");
  try {if(input.password()==null||input.password().isBlank())db.update("UPDATE users SET name=?,email=?,phone=? WHERE id=?",name,email,input.phone().trim(),current.id());else db.update("UPDATE users SET name=?,email=?,phone=?,password_hash=? WHERE id=?",name,email,input.phone().trim(),encoder.encode(input.password()),current.id());}
  catch(org.springframework.dao.DuplicateKeyException ex){throw error(409,"An account with this email already exists.");}
  User updated=find(current.id());var session=request.getSession(false);session.setAttribute("user:"+updated.role(),updated);session.setAttribute("user",updated);return updated;
 }
 @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest request) {
  var session=request.getSession(false);
  if(session!=null){User u=user(request);session.removeAttribute("user:"+u.role());if(session.getAttribute("user") instanceof User last&&last.role().equals(u.role()))session.removeAttribute("user");
   if(Collections.list(session.getAttributeNames()).stream().noneMatch(key->key.startsWith("user:")))session.invalidate();}
  return Map.of("ok",true);
 }

 @Configuration
 static class Security implements WebMvcConfigurer {
  @Override public void addInterceptors(InterceptorRegistry registry) {
   registry.addInterceptor(new HandlerInterceptor() {
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
     response.setHeader("X-Content-Type-Options","nosniff");
     response.setHeader("X-Frame-Options","DENY");
     response.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
     if(request.getRequestURI().startsWith("/api/")) {
      response.setHeader("Cache-Control","no-store");
      // Custom headers cannot be sent by cross-origin forms; CORS is intentionally disabled.
      if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && !"1".equals(request.getHeader("X-Shuttle-Request")))throw error(403,"Request verification failed.");
      if(!Set.of("/api/auth/login","/api/auth/register","/api/health").contains(request.getRequestURI()))user(request);
     }
     return true;
    }
   });
  }
  @Bean CommandLineRunner seed(JdbcTemplate db,@Value("${shuttle.seed-demo}") boolean enabled,@Value("${shuttle.demo-password}") String password) {
   return args -> {
    if(!enabled)return;
    if(password.length()<10)throw new IllegalStateException("DEMO_PASSWORD must have at least 10 characters.");
    var encoder=new BCryptPasswordEncoder();
    String[][] users={{"Alex Morgan","passenger@shuttle.local","PASSENGER",""},{"Jordan Lee","dispatch@shuttle.local","DISPATCHER",""},{"Sam Rivera","driver@shuttle.local","DRIVER","SH-101 | Mercedes Sprinter"},{"Taylor Brooks","driver2@shuttle.local","DRIVER","SH-102 | Ford Transit"}};
    for(var u:users) if(db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,u[1])==0)
     db.update("INSERT INTO users(name,email,password_hash,role,vehicle) VALUES(?,?,?,?,?)",u[0],u[1],encoder.encode(password),u[2],u[3]);
   };
  }
 }
}
