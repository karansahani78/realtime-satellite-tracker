package com.sattrack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sattrack.entity.ConjunctionAlert.RiskLevel;
import com.sattrack.entity.Notification.NotificationType;
import com.sattrack.entity.UserAlert.AlertType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrackingDto {

    // ─── Pass Prediction ─────────────────────────────────────────────────────

    @Data @Builder
    public static class PassRequest {
        private String  noradId;
        private double  observerLat;
        private double  observerLon;
        private double  observerAltMeters;
        private int     days;           // how many days ahead (1–10, default 7)
        private double  minElevation;   // minimum max-elevation to include (default 10°)
        private boolean visibleOnly;    // only passes where satellite is sunlit
    }

    @Data @Builder
    public static class PassSummary {
        private Long    id;
        private String  noradId;
        private String  satelliteName;

        private Instant aos;
        private Instant tca;
        private Instant los;

        private double  aosAzimuth;
        private String  aosDirection;   // "N", "NE", "E" …
        private double  tcaAzimuth;
        private double  losAzimuth;
        private String  losDirection;

        private double  maxElevation;
        private long    durationSeconds;
        private Double  magnitude;
        private boolean visible;        // naked-eye visible?

        /** Human-friendly: "6 min 23 sec" */
        private String  durationLabel;
    }

    // ─── Doppler ─────────────────────────────────────────────────────────────

    @Data @Builder
    public static class DopplerRequest {
        private String noradId;
        private double observerLat;
        private double observerLon;
        private double observerAltMeters;
        private double frequencyMhz;         // e.g. 437.550 for ISS APRS
    }

    @Data @Builder
    public static class DopplerResult {
        private String  noradId;
        private double  nominalFrequencyMhz;
        private double  dopplerShiftHz;       // positive = approaching, negative = receding
        private double  observedFrequencyMhz;
        private double  radialVelocityKms;    // km/s, + = approaching
        private double  elevationDeg;
        private double  rangKm;
        private Instant computedAt;
    }

    // ─── Conjunction ─────────────────────────────────────────────────────────

    @Data @Builder
    public static class ConjunctionSummary {
        private Long      id;
        private String    noradIdA;
        private String    satelliteNameA;
        private String    noradIdB;
        private String    satelliteNameB;
        private Instant   tca;
        private double    missDistanceKm;
        private double    relativeSpeedKms;
        private RiskLevel riskLevel;
        private Instant   computedAt;
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    @Data @Builder
    public static class NotificationDto {
        private Long             id;
        private NotificationType notificationType;
        private String           title;
        private String           message;
        private String           payload;
        private Instant          sentAt;
        private boolean          read;
        private Instant          readAt;
    }

    @Data @Builder
    public static class NotificationPage {
        private List<NotificationDto> notifications;
        private long                  unreadCount;
        private int                   totalPages;
        private long                  totalElements;
    }

    // ─── User Alert Preferences ──────────────────────────────────────────────

    @Data @Builder
    public static class AlertPreferenceRequest {
        private String    noradId;
        private AlertType alertType;
        private Double    minElevation;   // for PASS_OVERHEAD
        private boolean   visibleOnly;
        private int       leadTimeMinutes;
    }

    @Data @Builder
    public static class AlertPreferenceDto {
        private Long      id;
        private String    noradId;
        private String    satelliteName;
        private AlertType alertType;
        private Double    minElevation;
        private boolean   visibleOnly;
        private int       leadTimeMinutes;
        private boolean   active;
        private Instant   createdAt;
        private Instant   lastTriggeredAt;
    }
}