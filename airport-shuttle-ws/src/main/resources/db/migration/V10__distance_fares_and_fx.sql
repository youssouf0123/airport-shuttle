ALTER TABLE trips
 ADD COLUMN distance_km DECIMAL(10,3) NULL,
 ADD COLUMN usd_xof_rate DECIMAL(16,8) NOT NULL DEFAULT 500,
 ADD COLUMN usd_xof_rate_at TIMESTAMP NULL,
 ADD COLUMN fare_xof BIGINT NOT NULL DEFAULT 0;

UPDATE trips SET fare_xof=ROUND((fare_cents / 100.0) * usd_xof_rate) WHERE fare_xof=0;

CREATE TABLE exchange_rates (
 base_code CHAR(3) NOT NULL,
 target_code CHAR(3) NOT NULL,
 rate DECIMAL(16,8) NOT NULL,
 source_updated_at TIMESTAMP NULL,
 fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (base_code,target_code)
);
