package com.sattrack.repository;

import com.sattrack.entity.Satellite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface SatelliteRepository extends JpaRepository<Satellite, Long> {

    Optional<Satellite> findByNoradId(String noradId);

    boolean existsByNoradId(String noradId);

    Page<Satellite> findByActiveTrue(Pageable pageable);

    Page<Satellite> findByCategoryAndActiveTrue(String category, Pageable pageable);

    @Query("""
            SELECT s FROM Satellite s
            WHERE s.active = true
            AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR s.noradId = :query
                 OR LOWER(s.category) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Satellite> search(@Param("query") String query, Pageable pageable);

    List<Satellite> findByNoradIdIn(List<String> noradIds);

    @Query("SELECT DISTINCT s.category FROM Satellite s WHERE s.active = true ORDER BY s.category")
    List<String> findDistinctCategories();
}
