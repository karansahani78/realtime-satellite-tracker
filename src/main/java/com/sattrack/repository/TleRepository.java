package com.sattrack.repository;

import com.sattrack.entity.TleRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TleRepository extends JpaRepository<TleRecord, Long> {

    /**
     * Finds the most recent TLE for a satellite.
     * Critical hot path – backed by (satellite_id, epoch DESC) index.
     */
    @Query("""
            SELECT t FROM TleRecord t
            WHERE t.noradId = :noradId
            ORDER BY t.epoch DESC
            """)
    List<TleRecord> findLatestByNoradId(@Param("noradId") String noradId, Pageable pageable);

    default Optional<TleRecord> findLatestByNoradId(String noradId) {
        List<TleRecord> results = findLatestByNoradId(noradId,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT t.noradId FROM TleRecord t GROUP BY t.noradId")
    List<String> findAllTrackedNoradIds();

    boolean existsByNoradIdAndEpoch(String noradId, Instant epoch);

    /**
     * Prune old TLE records, keeping only the N most recent per satellite.
     * Called by scheduled cleanup job to prevent unbounded table growth.
     */
    @Modifying
    @Query(value = """
            DELETE FROM tle_records
            WHERE id NOT IN (
                SELECT id FROM (
                    SELECT id, ROW_NUMBER() OVER (PARTITION BY norad_id ORDER BY epoch DESC) AS rn
                    FROM tle_records
                ) ranked
                WHERE rn <= :keepCount
            )
            """, nativeQuery = true)
    int pruneOldTles(@Param("keepCount") int keepCount);
}
