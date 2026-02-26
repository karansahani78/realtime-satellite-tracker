package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Audit log of user tracking sessions.
 * Useful for analytics (most-tracked satellites), debugging, and compliance.
 */
@Entity
@Table(name = "tracking_logs", indexes = {
    @Index(name = "idx_tracking_user_time", columnList = "user_id, requested_at DESC"),
    @Index(name = "idx_tracking_satellite", columnList = "satellite_norad_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // nullable – anonymous requests are allowed

    @Column(name = "satellite_norad_id", nullable = false, length = 20)
    private String satelliteNoradId;

    @Column(name = "request_type", length = 20)
    private String requestType;  // "CURRENT", "PREDICT", "TRACK"

    @Column(name = "observer_latitude")
    private Double observerLatitude;

    @Column(name = "observer_longitude")
    private Double observerLongitude;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant requestedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;
}
