-- ============================================================
-- V3__tracking_features.sql
-- Pass predictions, conjunction alerts, notifications, user alerts
-- ============================================================


-- ============================================================
-- Sequences
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS pass_predictions_seq
    START 1 INCREMENT 50;

CREATE SEQUENCE IF NOT EXISTS conjunction_alerts_seq
    START 1 INCREMENT 20;

CREATE SEQUENCE IF NOT EXISTS user_alerts_seq
    START 1 INCREMENT 10;

CREATE SEQUENCE IF NOT EXISTS notifications_seq
    START 1 INCREMENT 50;


-- ============================================================
-- Pass Predictions
-- ============================================================

CREATE TABLE IF NOT EXISTS pass_predictions (
                                                id               BIGINT PRIMARY KEY DEFAULT nextval('pass_predictions_seq'),

                                                norad_id         VARCHAR(10)  NOT NULL,
                                                satellite_name   VARCHAR(100),

                                                observer_lat     DOUBLE PRECISION NOT NULL,
                                                observer_lon     DOUBLE PRECISION NOT NULL,
                                                observer_alt_m   DOUBLE PRECISION NOT NULL DEFAULT 0,

                                                aos              TIMESTAMPTZ NOT NULL,
                                                tca              TIMESTAMPTZ NOT NULL,
                                                los              TIMESTAMPTZ NOT NULL,

                                                aos_azimuth      DOUBLE PRECISION,
                                                tca_azimuth      DOUBLE PRECISION,
                                                los_azimuth      DOUBLE PRECISION,

                                                max_elevation    DOUBLE PRECISION NOT NULL,
                                                duration_seconds BIGINT,
                                                magnitude        DOUBLE PRECISION,
                                                visible          BOOLEAN NOT NULL DEFAULT FALSE,

                                                computed_at      TIMESTAMPTZ NOT NULL,
                                                user_id          BIGINT
);

CREATE INDEX IF NOT EXISTS idx_pass_norad_aos
    ON pass_predictions (norad_id, aos);

CREATE INDEX IF NOT EXISTS idx_pass_observer_norad
    ON pass_predictions (observer_lat, observer_lon, norad_id);

CREATE INDEX IF NOT EXISTS idx_pass_user_norad
    ON pass_predictions (user_id, norad_id);

CREATE INDEX IF NOT EXISTS idx_pass_los
    ON pass_predictions (los); -- for housekeeping deletes


-- ============================================================
-- Conjunction Alerts
-- ============================================================

CREATE TABLE IF NOT EXISTS conjunction_alerts (
                                                  id                 BIGINT PRIMARY KEY DEFAULT nextval('conjunction_alerts_seq'),

                                                  norad_id_a         VARCHAR(10) NOT NULL,
                                                  satellite_a        VARCHAR(100),

                                                  norad_id_b         VARCHAR(10) NOT NULL,
                                                  satellite_b        VARCHAR(100),

                                                  tca                TIMESTAMPTZ NOT NULL,
                                                  miss_distance_km   DOUBLE PRECISION NOT NULL,
                                                  relative_speed_kms DOUBLE PRECISION,

                                                  risk_level         VARCHAR(10) NOT NULL,
                                                  computed_at        TIMESTAMPTZ NOT NULL,
                                                  notification_sent  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_conj_tca
    ON conjunction_alerts (tca);

CREATE INDEX IF NOT EXISTS idx_conj_norad_a
    ON conjunction_alerts (norad_id_a);

CREATE INDEX IF NOT EXISTS idx_conj_norad_b
    ON conjunction_alerts (norad_id_b);

CREATE INDEX IF NOT EXISTS idx_conj_risk
    ON conjunction_alerts (risk_level, tca);


-- ============================================================
-- User Alerts (Preferences)
-- ============================================================

CREATE TABLE IF NOT EXISTS user_alerts (
                                           id                 BIGINT PRIMARY KEY DEFAULT nextval('user_alerts_seq'),

                                           user_id            BIGINT NOT NULL,
                                           norad_id           VARCHAR(10),

                                           alert_type         VARCHAR(20) NOT NULL,
                                           min_elevation      DOUBLE PRECISION,
                                           visible_only       BOOLEAN NOT NULL DEFAULT FALSE,
                                           lead_time_minutes  INT NOT NULL DEFAULT 10,
                                           active             BOOLEAN NOT NULL DEFAULT TRUE,

                                           created_at         TIMESTAMPTZ NOT NULL,
                                           last_triggered_at  TIMESTAMPTZ,

                                           CONSTRAINT uq_user_alert UNIQUE (user_id, norad_id, alert_type)
);

CREATE INDEX IF NOT EXISTS idx_ualert_user
    ON user_alerts (user_id);

CREATE INDEX IF NOT EXISTS idx_ualert_active
    ON user_alerts (active, alert_type);


-- ============================================================
-- Notifications
-- ============================================================

CREATE TABLE IF NOT EXISTS notifications (
                                             id                 BIGINT PRIMARY KEY DEFAULT nextval('notifications_seq'),

                                             user_id            BIGINT NOT NULL,
                                             notification_type  VARCHAR(30)  NOT NULL,
                                             title              VARCHAR(150) NOT NULL,
                                             message            VARCHAR(500) NOT NULL,
                                             payload            TEXT,

                                             sent_at            TIMESTAMPTZ NOT NULL,
                                             read               BOOLEAN NOT NULL DEFAULT FALSE,
                                             read_at            TIMESTAMPTZ,
                                             channel            VARCHAR(10)
);

CREATE INDEX IF NOT EXISTS idx_notif_user
    ON notifications (user_id, sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_notif_unread
    ON notifications (user_id, read)
    WHERE read = FALSE;

CREATE INDEX IF NOT EXISTS idx_notif_type
    ON notifications (notification_type);

CREATE INDEX IF NOT EXISTS idx_notif_sent_at
    ON notifications (sent_at); -- for housekeeping deletes