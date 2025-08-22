-- ======================================================================================================================
-- H2 DATABASE SCHEMA DEFINITION
--
-- This script defines the database schema for the H2 in-memory database, which is used for local development and
-- testing. It creates the necessary tables, indexes, and constraints required by the application.
--
-- This schema is intended for development environments only and will be created automatically by Spring Boot when the
-- 'h2' profile is active. It should not be used for production environments, which should have a more robust
-- database like PostgreSQL and a proper migration strategy (e.g., using Flyway or Liquibase).
-- ======================================================================================================================

-- Drop tables if they exist to ensure a clean slate on each application startup.
DROP TABLE IF EXISTS user_profiles;
DROP TABLE IF EXISTS users;

-- Create the 'users' table to store core user information.
CREATE TABLE users (
    -- A universally unique identifier for the user.
    id UUID PRIMARY KEY,

    -- The user's chosen username, which must be unique.
    username VARCHAR(50) NOT NULL UNIQUE,

    -- The user's email address, also unique.
    email VARCHAR(100) NOT NULL UNIQUE,

    -- The user's hashed password.
    password VARCHAR(255) NOT NULL,

    -- The user's first name.
    first_name VARCHAR(50) NOT NULL,

    -- The user's last name.
    last_name VARCHAR(50) NOT NULL,

    -- A flag to indicate if the user's account is active.
    is_active BOOLEAN NOT NULL,

    -- A flag to indicate if the user has verified their email address.
    is_email_verified BOOLEAN NOT NULL,

    -- A counter for failed login attempts, useful for security throttling.
    login_attempts INT NOT NULL,

    -- The timestamp of the user's last successful login.
    last_login_at TIMESTAMP,

    -- A version number for optimistic locking.
    version BIGINT,

    -- The timestamp when the user was created.
    created_at TIMESTAMP NOT NULL,

    -- The timestamp of the last update to the user's record.
    updated_at TIMESTAMP,

    -- The identifier of the user/system that created this record.
    created_by VARCHAR(255),

    -- The identifier of the user/system that last updated this record.
    updated_by VARCHAR(255)
);

-- Create indexes to improve query performance on frequently searched columns.
CREATE INDEX idx_username ON users(username);
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_active_status ON users(is_active);
CREATE INDEX idx_created_at ON users(created_at);

-- Create the 'user_profiles' table to store additional, non-essential user details.
CREATE TABLE user_profiles (
    -- A unique identifier for the profile.
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- The user's first name.
    first_name VARCHAR(100) NOT NULL,

    -- The user's last name.
    last_name VARCHAR(100) NOT NULL,

    -- The user's phone number.
    phone_number VARCHAR(20),

    -- The user's date of birth.
    date_of_birth TIMESTAMP,

    -- The user's physical address.
    address VARCHAR(500),

    -- The city part of the address.
    city VARCHAR(100),

    -- The state or region part of the address.
    state VARCHAR(100),

    -- The country part of the address.
    country VARCHAR(100),

    -- The postal code.
    postal_code VARCHAR(20),

    -- User-specific preferences, stored as a flexible text field (e.g., JSON).
    preferences TEXT,

    -- The timestamp when the profile was created.
    created_at TIMESTAMP NOT NULL,

    -- The timestamp of the last update to the profile.
    updated_at TIMESTAMP NOT NULL,

    -- A foreign key linking this profile to a user in the 'users' table.
    user_id UUID NOT NULL,

    -- Defines the foreign key constraint to ensure data integrity.
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
);