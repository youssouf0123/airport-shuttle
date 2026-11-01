CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL, email VARCHAR(190) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL,
 vehicle VARCHAR(100), seats INT NOT NULL DEFAULT 8, luggage_capacity INT NOT NULL DEFAULT 12
);
CREATE TABLE locations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(150) NOT NULL, detail VARCHAR(200) NOT NULL,
 lat DOUBLE NOT NULL, lng DOUBLE NOT NULL, airport BOOLEAN NOT NULL
);
INSERT INTO locations(name,detail,lat,lng,airport) VALUES
 ('JFK Terminal 4','Arrivals, passenger pickup zone B',40.6447,-73.7823,true),
 ('JFK Terminal 5','Arrivals, passenger pickup zone C',40.6453,-73.7769,true),
 ('JFK Terminal 8','Arrivals, passenger pickup zone A',40.6468,-73.7905,true),
 ('Grand Central Terminal','42nd Street at Vanderbilt Avenue',40.7527,-73.9772,false),
 ('Penn Station','8th Avenue at West 31st Street',40.7506,-73.9935,false),
 ('Times Square','West 43rd Street at 8th Avenue',40.7573,-73.9897,false),
 ('Downtown Brooklyn','Marriott, 333 Adams Street',40.6933,-73.9883,false);
CREATE TABLE trips (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, passenger_id BIGINT NOT NULL, driver_id BIGINT,
 pickup_id BIGINT NOT NULL, destination_id BIGINT NOT NULL, via_id BIGINT,
 flight VARCHAR(20) NOT NULL, pickup_at TIMESTAMP NOT NULL,
 passengers INT NOT NULL, bags INT NOT NULL, notes VARCHAR(500) NOT NULL DEFAULT '',
 dispatch_notes VARCHAR(500) NOT NULL DEFAULT '', status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
 fare_cents INT NOT NULL, version INT NOT NULL DEFAULT 0,
 driver_lat DOUBLE, driver_lng DOUBLE, location_at TIMESTAMP NULL, eta_minutes INT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 request_key VARCHAR(80) NOT NULL,
 FOREIGN KEY (passenger_id) REFERENCES users(id), FOREIGN KEY (driver_id) REFERENCES users(id),
 FOREIGN KEY (pickup_id) REFERENCES locations(id), FOREIGN KEY (destination_id) REFERENCES locations(id), FOREIGN KEY (via_id) REFERENCES locations(id),
 UNIQUE KEY uq_request(passenger_id,request_key), INDEX ix_driver(driver_id,status), INDEX ix_pickup(pickup_at)
);
CREATE TABLE trip_events (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, trip_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL, detail VARCHAR(600) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (trip_id) REFERENCES trips(id), FOREIGN KEY (actor_id) REFERENCES users(id)
);
CREATE TABLE notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, trip_id BIGINT NOT NULL,
 kind VARCHAR(40) NOT NULL, message VARCHAR(250) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 read_at TIMESTAMP NULL, UNIQUE KEY uq_notice(trip_id,kind), FOREIGN KEY (user_id) REFERENCES users(id), FOREIGN KEY (trip_id) REFERENCES trips(id)
);
CREATE TABLE app_settings (setting_key VARCHAR(50) PRIMARY KEY, setting_value TEXT NOT NULL);
CREATE TABLE push_subscriptions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,endpoint VARCHAR(1500) NOT NULL,
 endpoint_hash CHAR(64) NOT NULL UNIQUE,p256dh VARCHAR(200) NOT NULL,auth VARCHAR(200) NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE push_deliveries (
 notification_id BIGINT NOT NULL,subscription_id BIGINT NOT NULL,attempts INT NOT NULL DEFAULT 0,
 delivered BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY(notification_id,subscription_id),
 FOREIGN KEY (notification_id) REFERENCES notifications(id),FOREIGN KEY (subscription_id) REFERENCES push_subscriptions(id) ON DELETE CASCADE
);
