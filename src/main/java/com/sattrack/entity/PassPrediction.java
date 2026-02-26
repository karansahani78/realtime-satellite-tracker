package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pass_predictions", indexes = {
        @Index(name = "idx_pass_norad_aos",      columnList = "norad_id, aos"),
        @Index(name = "idx_pass_observer_norad",  columnList = "observer_lat, observer_lon, norad_id"),
        @Index(name = "idx_pass_user_norad",      columnList = "user_id, norad_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PassPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pass_seq")
    @SequenceGenerator(name = "pass_seq", sequenceName = "pass_predictions_seq", allocationSize = 50)
    private Long id;

    @Column(name = "norad_id", nullable = false, length = 10)
    private String noradId;

    @Column(name = "satellite_name", length = 100)
    private String satelliteName;

    /** Observer coordinates this prediction was calculated for */
    @Column(name = "observer_lat", nullable = false)
    private double observerLat;

    @Column(name = "observer_lon", nullable = false)
    private double observerLon;

    @Column(name = "observer_alt_m")
    private double observerAltMeters;

    // ── Pass window ──────────────────────────────────────────────────────────
    @Column(name = "aos", nullable = false)           private Instant aos;          // Acquisition of Signal
    @Column(name = "tca", nullable = false)           private Instant tca;          // Time of Closest Approach / max el
    @Column(name = "los", nullable = false)           private Instant los;          // Loss of Signal

    @Column(name = "aos_azimuth")                     private double aosAzimuth;    // degrees
    @Column(name = "tca_azimuth")                     private double tcaAzimuth;
    @Column(name = "los_azimuth")                     private double losAzimuth;

    @Column(name = "max_elevation", nullable = false) private double maxElevation;  // degrees
    @Column(name = "duration_seconds")                private long   durationSeconds;

    /** Estimated visual magnitude (lower = brighter). Null if unknown. */
    @Column(name = "magnitude")
    private Double magnitude;

    /** True if the pass occurs in darkness and satellite is sunlit */
    @Column(name = "visible")
    private boolean visible;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    /** Which user this prediction was computed for (null = anonymous / batch) */
    @Column(name = "user_id")
    private Long userId;
}