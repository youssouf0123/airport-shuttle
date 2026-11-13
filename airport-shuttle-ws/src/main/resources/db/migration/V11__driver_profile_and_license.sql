ALTER TABLE users ADD COLUMN address VARCHAR(500) NULL;

CREATE TABLE driver_documents (
 driver_id BIGINT PRIMARY KEY,
 file_name VARCHAR(180) NOT NULL,
 content_type VARCHAR(80) NOT NULL,
 file_size INT NOT NULL,
 content MEDIUMBLOB NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_driver_documents_user FOREIGN KEY (driver_id) REFERENCES users(id)
);
