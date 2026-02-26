package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core satellite metadata entity.
 *
 * noradId is the authoritative identifier used across NORAD, CelesTrak, and
 * Space-Track. We store it as a string (not int) because legacy catalogs
 * occasionally contain alpha-numeric suffixes.
 *
 * Indexing strategy:
 * - noradId: unique index for O(1) TLE lookups
 * - category: composite index with name for category-filtered searches
 * - active: partial index candidate on the DB side (see migration script)
 */
@Entity
@Table(name = "satellites", indexes = {
    @Index(name = "idx_satellite_norad_id", columnList = "norad_id", unique = true),
    @Index(name = "idx_satellite_category_name", columnList = "category, name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Satellite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "norad_id", nullable = false, unique = true, length = 20)
    private String noradId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;   // e.g. "ISS", "STARLINK", "WEATHER", "GPS"

    @Column(length = 500)
    private String description;

    @Column(name = "international_designator", length = 20)
    private String internationalDesignator;

    @Column(name = "launch_date")
    private Instant launchDate;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Latest TLE is at index 0 after ordering by epoch DESC
    @OneToMany(mappedBy = "satellite", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("epoch DESC")
    @Builder.Default
    private List<TleRecord> tleHistory = new ArrayList<>();
}
