package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_alerts", indexes = {
        @Index(name = "idx_ualert_user",       columnList = "user_id"),
        @Index(name = "idx_ualert_user_norad",  columnList = "user_id, norad_id"),
        @Index(name = "idx_ualert_active",      columnList = "active, alert_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ualert_seq")
    @SequenceGenerator(name = "ualert_seq", sequenceName = "user_alerts_seq", allocationSize = 10)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "norad_id", length = 10)
    private String noradId;          // null = all satellites (for conjunction alerts)

    @Column(name = "alert_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    /** Minimum elevation (degrees) to trigger a pass alert */
    @Column(name = "min_elevation")
    private Double minElevation;

    /** Only alert for visible passes (satellite sunlit + observer dark) */
    @Column(name = "visible_only")
    private boolean visibleOnly;

    /** Minutes before AOS to send the notification */
    @Column(name = "lead_time_minutes")
    private int leadTimeMinutes;

    @Column(name = "active")
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    public enum AlertType {
        PASS_OVERHEAD,      // satellite passing over observer location
        CONJUNCTION,        // close approach between two satellites
        TLE_STALE,          // TLE hasn't been updated in N days
        REENTRY_WARNING     // satellite decay / re-entry imminent
    }
}