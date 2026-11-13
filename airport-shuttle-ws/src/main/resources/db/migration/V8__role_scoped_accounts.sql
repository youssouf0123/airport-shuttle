ALTER TABLE users DROP INDEX email;
ALTER TABLE users ADD UNIQUE KEY uq_user_email_role (email, role);
