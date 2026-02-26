package com.sattrack.repository;

import com.sattrack.entity.ConjunctionAlert;
import com.sattrack.entity.ConjunctionAlert.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ConjunctionAlertRepository extends JpaRepository<ConjunctionAlert, Long> {

    @Query("""
            SELECT c FROM ConjunctionAlert c
            WHERE c.tca >= :from
              AND c.tca <= :to
            ORDER BY c.missDistanceKm ASC
            """)
    Page<ConjunctionAlert> findUpcoming(
            @Param("from") Instant from,
            @Param("to")   Instant to,
            Pageable pageable);

    @Query("""
            SELECT c FROM ConjunctionAlert c
            WHERE (c.noradIdA = :noradId OR c.noradIdB = :noradId)
              AND c.tca >= :from
            ORDER BY c.tca ASC
            """)
    List<ConjunctionAlert> findBySatellite(
            @Param("noradId") String noradId,
            @Param("from")    Instant from);

    @Query("""
            SELECT c FROM ConjunctionAlert c
            WHERE c.riskLevel IN :levels
              AND c.tca BETWEEN :from AND :to
              AND c.notificationSent = false
            ORDER BY c.missDistanceKm ASC
            """)
    List<ConjunctionAlert> findUnnotifiedByRisk(
            @Param("levels") List<RiskLevel> levels,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /** Avoid duplicate alerts for same pair within 6-hour window */
    @Query("""
            SELECT COUNT(c) > 0 FROM ConjunctionAlert c
            WHERE ((c.noradIdA = :a AND c.noradIdB = :b)
                OR (c.noradIdA = :b AND c.noradIdB = :a))
              AND c.tca BETWEEN :windowStart AND :windowEnd
            """)
    boolean existsForPairInWindow(
            @Param("a")           String noradIdA,
            @Param("b")           String noradIdB,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd")   Instant windowEnd);

    @Modifying
    @Query("DELETE FROM ConjunctionAlert c WHERE c.tca < :before")
    int deleteOlderThan(@Param("before") Instant before);
}