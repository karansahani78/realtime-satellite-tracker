package com.sattrack.repository;

import com.sattrack.entity.UserAlert;
import com.sattrack.entity.UserAlert.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findByUserIdAndActiveTrue(Long userId);

    List<UserAlert> findByUserIdAndNoradIdAndActiveTrue(Long userId, String noradId);

    @Query("""
            SELECT a FROM UserAlert a
            WHERE a.active = true
              AND a.alertType = :type
            """)
    List<UserAlert> findAllActiveByType(@Param("type") AlertType type);

    @Query("""
            SELECT a FROM UserAlert a
            WHERE a.active = true
              AND a.alertType = :type
              AND a.noradId = :noradId
            """)
    List<UserAlert> findActiveByTypeAndNoradId(
            @Param("type")    AlertType type,
            @Param("noradId") String noradId);

    Optional<UserAlert> findByUserIdAndNoradIdAndAlertType(
            Long userId, String noradId, AlertType alertType);

    void deleteByUserIdAndNoradIdAndAlertType(
            Long userId, String noradId, AlertType alertType);
}