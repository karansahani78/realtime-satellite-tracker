-- ============================================================
-- V1: Initial Schema
-- Satellite Tracking Platform
-- ============================================================

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',

    -- Observer location for pass predictions
    default_latitude       DOUBLE PRECISION,
    default_longitude      DOUBLE PRECISION,
    default_altitude_m     DOUBLE PRECISION DEFAULT 0.0,
    timezone_id            VARCHAR(50) DEFAULT 'UTC',

    is_enabled       BOOLEAN     NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

-- Index for username lookups (login)
CREATE INDEX idx_user_username ON users (username);
CREATE INDEX idx_user_email    ON users (email);

-- ============================================================
-- USER FAVORITE SATELLITES (element collection)
-- ============================================================
CREATE TABLE user_favorite_satellites (
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    satellite_norad_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, satellite_norad_id)
);

-- ============================================================
-- SATELLITES
-- ============================================================
CREATE TABLE satellites (
    id                        BIGSERIAL PRIMARY KEY,
    norad_id                  VARCHAR(20)  NOT NULL,
    name                      VARCHAR(100) NOT NULL,
    category                  VARCHAR(50),
    description               VARCHAR(500),
    international_designator  VARCHAR(20),
    launch_date               TIMESTAMPTZ,
    country_code              VARCHAR(5),
    is_active                 BOOLEAN     NOT NULL DEFAULT true,
    image_url                 VARCHAR(500),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_satellites_norad_id UNIQUE (norad_id)
);

-- Unique index on norad_id (O(1) lookups by NORAD catalog number)
CREATE UNIQUE INDEX idx_satellite_norad_id    ON satellites (norad_id);

-- Composite index for category-filtered list views
CREATE INDEX idx_satellite_category_name     ON satellites (category, name);

-- Partial index: active satellites only (most queries filter inactive out)
CREATE INDEX idx_satellite_active            ON satellites (name) WHERE is_active = true;

-- Full-text search index for satellite name search
CREATE INDEX idx_satellite_name_fts          ON satellites USING GIN (to_tsvector('english', name));

-- ============================================================
-- TLE RECORDS
-- ============================================================
CREATE TABLE tle_records (
    id          BIGSERIAL PRIMARY KEY,
    satellite_id BIGINT       NOT NULL REFERENCES satellites(id) ON DELETE CASCADE,
    norad_id    VARCHAR(20)  NOT NULL,   -- denormalized for query performance
    line0       VARCHAR(24),             -- satellite name from TLE file
    line1       VARCHAR(70)  NOT NULL,   -- TLE line 1
    line2       VARCHAR(70)  NOT NULL,   -- TLE line 2
    epoch       TIMESTAMPTZ  NOT NULL,   -- parsed from TLE line 1
    source      VARCHAR(50),             -- data provider (celestrak, space-track, ...)
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Deduplication: one record per (norad_id, epoch) pair
    CONSTRAINT uq_tle_norad_epoch UNIQUE (norad_id, epoch)
);

-- Primary lookup: latest TLE for a satellite (most critical query path)
CREATE INDEX idx_tle_satellite_epoch ON tle_records (satellite_id, epoch DESC);

-- Lookup by NORAD ID without satellite join
CREATE INDEX idx_tle_norad_id        ON tle_records (norad_id, epoch DESC);

-- Time-range queries for historical replay
CREATE INDEX idx_tle_epoch           ON tle_records (epoch);

-- ============================================================
-- TRACKING LOGS
-- ============================================================
CREATE TABLE tracking_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES users(id) ON DELETE SET NULL,  -- nullable (anonymous)
    satellite_norad_id  VARCHAR(20) NOT NULL,
    request_type        VARCHAR(20),   -- CURRENT, PREDICT, TRACK
    observer_latitude   DOUBLE PRECISION,
    observer_longitude  DOUBLE PRECISION,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_ip           VARCHAR(45)   -- supports IPv6
);

-- Most common query: user's recent tracking history
CREATE INDEX idx_tracking_user_time  ON tracking_logs (user_id, requested_at DESC);

-- Analytics: most tracked satellites
CREATE INDEX idx_tracking_satellite  ON tracking_logs (satellite_norad_id);

-- Range queries for audit/rate-limit checks
CREATE INDEX idx_tracking_ip_time    ON tracking_logs (client_ip, requested_at DESC);

-- ============================================================
-- SEED DATA: Critical satellites
-- These are inserted immediately so the app is useful out of the box.
-- TLE data will be populated by the TLE refresh scheduler on startup.
-- ============================================================
INSERT INTO satellites (norad_id, name, category, description, country_code) VALUES
    ('25544',  'ISS (ZARYA)',            'ISS & Stations', 'International Space Station', 'US'),
    ('20580',  'HST',                    'Science',        'Hubble Space Telescope',       'US'),
    ('43226',  'CSS (TIANHE)',           'ISS & Stations', 'Chinese Space Station',        'CN'),
    ('37849',  'TIANGONG 1',            'ISS & Stations', 'Chinese Space Lab (defunct)',   'CN'),
    ('48274',  'STARLINK-1007',         'Starlink',        'SpaceX Starlink satellite',     'US'),
    ('32060',  'NOAA 18',               'Weather',         'NOAA Weather Satellite',        'US'),
    ('33591',  'NOAA 19',               'Weather',         'NOAA Weather Satellite',        'US'),
    ('41866',  'GOES 16',               'Weather',         'NOAA GOES-16 GEO Weather',      'US'),
    ('28654',  'NOAA 18',               'Weather',         'NOAA Weather Satellite 18',     'US'),
    ('24876',  'GPS BIIR-2  (PRN 13)',  'GPS',             'GPS Navigation Satellite',      'US');
