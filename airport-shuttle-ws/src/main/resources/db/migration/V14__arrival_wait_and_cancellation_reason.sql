ALTER TABLE trips
 ADD COLUMN arrived_at TIMESTAMP NULL,
 ADD COLUMN wait_seconds INT NULL,
 ADD COLUMN cancellation_reason VARCHAR(40) NULL;
