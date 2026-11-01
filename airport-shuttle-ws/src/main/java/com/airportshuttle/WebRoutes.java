package com.airportshuttle;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class WebRoutes {
 @GetMapping({"/passenger","/passenger/"}) String passenger(){return "forward:/passenger/index.html";}
 @GetMapping({"/passenger/profile","/passenger/profile/","/passenger/profile/info","/passenger/profile/info/"}) String passengerProfile(){return "forward:/passenger/index.html";}
 @GetMapping({"/dispatcher","/dispatcher/"}) String dispatcher(){return "forward:/dispatcher/index.html";}
 @GetMapping({"/dispatcher/drivers","/dispatcher/drivers/"}) String dispatcherDrivers(){return "forward:/dispatcher/index.html";}
 @GetMapping({"/dispatcher/profile","/dispatcher/profile/","/dispatcher/profile/info","/dispatcher/profile/info/"}) String dispatcherProfile(){return "forward:/dispatcher/index.html";}
 @GetMapping({"/driver","/driver/"}) String driver(){return "forward:/driver/index.html";}
 @GetMapping({"/driver/profile","/driver/profile/","/driver/profile/info","/driver/profile/info/"}) String driverProfile(){return "forward:/driver/index.html";}
}
