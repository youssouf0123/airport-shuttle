CREATE TABLE profile_photos (
    user_id BIGINT PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_photos_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
