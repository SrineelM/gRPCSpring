-- ======================================================================================================================
-- H2 DATABASE SEED DATA
--
-- This script inserts initial data into the H2 in-memory database for local development and testing purposes.
-- It populates the 'users' and 'user_profiles' tables with sample records, which is useful for immediate
-- testing of the application's functionality without needing to manually create users.
--
-- This data is intended for development environments only and will be loaded when the 'h2' Spring profile is active.
-- It should not be used in production.
-- ======================================================================================================================

-- Insert sample users into the 'users' table.
-- These users can be used to test authentication and basic application flows.
INSERT INTO users (id, username, email, password, first_name, last_name, is_active, is_email_verified, login_attempts, last_login_at, version, created_at, updated_at, created_by, updated_by)
VALUES
  -- A fully active and verified user.
  ('11111111-1111-1111-1111-111111111111', 'alice', 'alice@example.com', '$2a$10$...placeholder...', 'Alice', 'Smith', TRUE, TRUE, 0, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),

  -- An active user whose email is not yet verified.
  ('22222222-2222-2222-2222-222222222222', 'bob', 'bob@example.com', '$2a$10$dummypassword...', 'Bob', 'Jones', TRUE, FALSE, 0, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

-- Insert corresponding user profiles into the 'user_profiles' table.
-- This data provides additional details for the sample users.
INSERT INTO user_profiles (first_name, last_name, phone_number, date_of_birth, address, city, state, country, postal_code, preferences, created_at, updated_at, user_id)
VALUES
  -- Profile for Alice.
  ('Alice', 'Smith', '1234567890', '1990-01-01 00:00:00', '123 Main St', 'Metropolis', 'State1', 'Country1', '12345', 'prefers_email', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '11111111-1111-1111-1111-111111111111'),

  -- Profile for Bob.
  ('Bob', 'Jones', '0987654321', '1985-05-05 00:00:00', '456 Side St', 'Gotham', 'State2', 'Country2', '54321', 'prefers_sms', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '22222222-2222-2222-2222-222222222222');
