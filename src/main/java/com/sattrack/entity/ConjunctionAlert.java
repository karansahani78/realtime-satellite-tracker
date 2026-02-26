package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "conjunction_alerts", indexes = {
        @Index(name = "idx_conj_tca",        columnList = "tca"),
        @Index(name = "idx_conj_norad_a",     columnList = "norad_id_a"),
        @Index(name = "idx_conj_norad_b",     columnList = "norad_id_b"),
        @Index(name = "idx_conj_risk",        columnList = "risk_level, tca")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConjunctionAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "conj_seq")
    @SequenceGenerator(name = "conj_seq", sequenceName = "conjunction_alerts_seq", allocationSize = 20)
    private Long id;

    @Column(name = "norad_id_a", nullable = false, length = 10)  private String noradIdA;
    @Column(name = "satellite_a", length = 100)                   private String satelliteNameA;

    @Column(name = "norad_id_b", nullable = false, length = 10)  private String noradIdB;
    @Column(name = "satellite_b", length = 100)                   private String satelliteNameB;

    /** Time of Closest Approach */
    @Column(name = "tca", nullable = false)
    private Instant tca;

    /** Minimum distance in kilometres */
    @Column(name = "miss_distance_km", nullable = false)
    private double missDistanceKm;

    /** Relative speed at TCA (km/s) */
    @Column(name = "relative_speed_kms")
    private double relativeSpeedKms;

    @Column(name = "risk_level", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "notification_sent")
    private boolean notificationSent;

    public enum RiskLevel {
        /** > 5 km */       LOW,
        /** 1–5 km */       MEDIUM,
        /** 0.2–1 km */     HIGH,
        /** < 0.2 km */     CRITICAL
    }

    public static RiskLevel riskFrom(double distanceKm) {
        if (distanceKm < 0.2)  return RiskLevel.CRITICAL;
        if (distanceKm < 1.0)  return RiskLevel.HIGH;
        if (distanceKm < 5.0)  return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}