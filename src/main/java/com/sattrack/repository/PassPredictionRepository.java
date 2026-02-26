package com.sattrack.repository;

import com.sattrack.entity.PassPrediction;
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
public interface PassPredictionRepository extends JpaRepository<PassPrediction, Long> {

    @Query("""
            SELECT p FROM PassPrediction p
            WHERE p.noradId = :noradId
              AND p.observerLat BETWEEN :lat - 0.05 AND :lat + 0.05
              AND p.observerLon BETWEEN :lon - 0.05 AND :lon + 0.05
              AND p.aos >= :from
              AND p.aos <= :to
            ORDER BY p.aos ASC
            """)
    List<PassPrediction> findUpcomingPasses(
            @Param("noradId") String noradId,
            @Param("lat")     double lat,
            @Param("lon")     double lon,
            @Param("from")    Instant from,
            @Param("to")      Instant to);

    @Query("""
            SELECT p FROM PassPrediction p
            WHERE p.userId = :userId
              AND p.aos >= :from
            ORDER BY p.aos ASC
            """)
    Page<PassPrediction> findByUserIdAndAosAfter(
            @Param("userId") Long userId,
            @Param("from")   Instant from,
            Pageable pageable);

    @Query("""
            SELECT p FROM PassPrediction p
            WHERE p.userId = :userId
              AND p.aos BETWEEN :from AND :to
              AND (:visibleOnly = false OR p.visible = true)
            ORDER BY p.aos ASC
            """)
    List<PassPrediction> findUserPasses(
            @Param("userId")      Long userId,
            @Param("from")        Instant from,
            @Param("to")          Instant to,
            @Param("visibleOnly") boolean visibleOnly);

    /** Housekeeping — delete old computed predictions */
    @Modifying
    @Query("DELETE FROM PassPrediction p WHERE p.los < :before")
    int deleteExpiredPasses(@Param("before") Instant before);

    /** Check if we already computed passes for this satellite+location+window */
    boolean existsByNoradIdAndObserverLatBetweenAndObserverLonBetweenAndAosAfter(
            String noradId,
            double latMin, double latMax,
            double lonMin, double lonMax,
            Instant from);
}