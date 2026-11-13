CREATE INDEX ix_trip_history_status_pickup ON trips(status, pickup_at, id);
CREATE INDEX ix_trip_history_driver_status_pickup ON trips(driver_id, status, pickup_at, id);
CREATE INDEX ix_trip_history_passenger_status_pickup ON trips(passenger_id, status, pickup_at, id);
