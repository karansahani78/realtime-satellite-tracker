package com.sattrack.service;

import com.sattrack.dto.TrackingDto.AlertPreferenceDto;
import com.sattrack.dto.TrackingDto.AlertPreferenceRequest;
import com.sattrack.entity.UserAlert;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.UserAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAlertService {

    private final UserAlertRepository userAlertRepo;
    private final SatelliteRepository satelliteRepo;

    public List<AlertPreferenceDto> getForUser(Long userId) {
        return userAlertRepo.findByUserIdAndActiveTrue(userId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public AlertPreferenceDto create(Long userId, AlertPreferenceRequest req) {
        // Upsert: if same user+noradId+type exists, update it
        UserAlert alert = userAlertRepo
                .findByUserIdAndNoradIdAndAlertType(userId, req.getNoradId(), req.getAlertType())
                .orElse(UserAlert.builder()
                        .userId(userId)
                        .noradId(req.getNoradId())
                        .alertType(req.getAlertType())
                        .createdAt(Instant.now())
                        .build());

        alert.setMinElevation(req.getMinElevation());
        alert.setVisibleOnly(req.isVisibleOnly());
        alert.setLeadTimeMinutes(req.getLeadTimeMinutes() > 0 ? req.getLeadTimeMinutes() : 10);
        alert.setActive(true);

        return toDto(userAlertRepo.save(alert));
    }

    @Transactional
    public void delete(Long alertId, Long userId) {
        UserAlert alert = userAlertRepo.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        if (!alert.getUserId().equals(userId))
            throw new AccessDeniedException("Not your alert");
        alert.setActive(false);
        userAlertRepo.save(alert);
    }

    private AlertPreferenceDto toDto(UserAlert a) {
        String satName = a.getNoradId() != null
                ? satelliteRepo.findByNoradId(a.getNoradId())
                .map(s -> s.getName()).orElse(a.getNoradId())
                : null;

        return AlertPreferenceDto.builder()
                .id(a.getId())
                .noradId(a.getNoradId())
                .satelliteName(satName)
                .alertType(a.getAlertType())
                .minElevation(a.getMinElevation())
                .visibleOnly(a.isVisibleOnly())
                .leadTimeMinutes(a.getLeadTimeMinutes())
                .active(a.isActive())
                .createdAt(a.getCreatedAt())
                .lastTriggeredAt(a.getLastTriggeredAt())
                .build();
    }
}