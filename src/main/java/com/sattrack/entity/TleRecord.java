package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Stores raw Two-Line Element sets with full history.
 *
 * Why keep history? TLEs degrade over time due to atmospheric drag and
 * orbital maneuvers. Storing history allows:
 * 1. Replaying historical passes (forensic / educational use)
 * 2. Detecting maneuvers (sudden epoch jumps)
 * 3. Auditing data quality from different providers
 *
 * Performance note: most queries only need the latest TLE, so the repository
 * exposes a findLatestByNoradId() method backed by the epoch index.
 */
@Entity
@Table(name = "tle_records", indexes = {
    @Index(name = "idx_tle_satellite_epoch", columnList = "satellite_id, epoch DESC"),
    @Index(name = "idx_tle_norad_id", columnList = "norad_id"),
    @Index(name = "idx_tle_epoch", columnList = "epoch")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TleRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satellite_id", nullable = false)
    private Satellite satellite;

    // Denormalized for fast queries without joining satellites table
    @Column(name = "norad_id", nullable = false, length = 20)
    private String noradId;

    @Column(name = "line0", length = 24)
    private String line0;   // Name line (optional in raw TLE)

    @Column(name = "line1", nullable = false, length = 70)
    private String line1;

    @Column(name = "line2", nullable = false, length = 70)
    private String line2;

    /**
     * TLE epoch as absolute UTC instant.
     * Parsed from the TLE line1 epoch field (YYDDD.DDDDDDDD format).
     * Stored as Instant for accurate time arithmetic in propagation.
     */
    @Column(name = "epoch", nullable = false)
    private Instant epoch;

    @Column(name = "source", length = 50)
    private String source;   // e.g. "celestrak", "space-track"

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt;
}
