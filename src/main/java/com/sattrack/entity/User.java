package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 10)
    @Builder.Default
    private String role = "ROLE_USER";

    // Observer location for pass predictions
    @Column(name = "default_latitude")
    private Double defaultLatitude;

    @Column(name = "default_longitude")
    private Double defaultLongitude;

    @Column(name = "default_altitude_m")
    @Builder.Default
    private Double defaultAltitudeMeters = 0.0;

    @Column(name = "timezone_id", length = 50)
    @Builder.Default
    private String timezoneId = "UTC";

    @Column(name = "is_enabled")
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TrackingLog> trackingLogs = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_favorite_satellites",
                     joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "satellite_norad_id")
    @Builder.Default
    private List<String> favoriteSatelliteIds = new ArrayList<>();
}
