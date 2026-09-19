-- ─────────────────────────────────────────────────────────
-- Notification Engine - Full Database Schema
-- ─────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS notification_engine;
USE notification_engine;

-- ─── Users (Admin + Regular) ──────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─── Notifications ────────────────────────────────────────
-- Core table. One row per notification request.
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient       VARCHAR(255) NOT NULL,          -- email address or phone
    channel         ENUM('EMAIL', 'SMS') NOT NULL,
    subject         VARCHAR(255),                   -- for EMAIL only
    body            TEXT NOT NULL,
    status          ENUM(
                        'PENDING',      -- just submitted, not tried yet
                        'PROCESSING',   -- scheduler picked it up right now
                        'SCHEDULED',    -- failed once, waiting for next retry
                        'DELIVERED',    -- successfully sent
                        'DEAD'          -- max retries exhausted, give up
                    ) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    next_retry_at   DATETIME,                       -- when to try again
    submitted_by    BIGINT,                         -- FK to users
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_status (status),
    INDEX idx_next_retry (next_retry_at),
    INDEX idx_created_at (created_at)
);

-- ─── Notification Attempts ────────────────────────────────
-- Every single send attempt is recorded here (success or fail).
-- This is the full audit trail.
CREATE TABLE IF NOT EXISTS notification_attempts (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id  BIGINT NOT NULL,
    attempt_number   INT NOT NULL,               -- 1st try, 2nd try, etc.
    attempted_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success          BOOLEAN NOT NULL,
    failure_reason   TEXT,                       -- full error message if failed
    response_time_ms BIGINT,                     -- how long the send took

    FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    INDEX idx_notification_id (notification_id),
    INDEX idx_attempted_at (attempted_at)
);

-- ─── Seed: default admin user ─────────────────────────────
-- Password: admin123 (BCrypt hashed)
INSERT IGNORE INTO users (name, email, password, role)
VALUES (
    'Admin',
    'admin@notificationengine.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh8y',
    'ADMIN'
);
