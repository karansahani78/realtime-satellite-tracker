package com.sattrack.scheduler;

import com.sattrack.dto.TrackingDto.PassRequest;
import com.sattrack.entity.PassPrediction;
import com.sattrack.entity.UserAlert;
import com.sattrack.entity.UserAlert.AlertType;
import com.sattrack.repository.NotificationRepository;
import com.sattrack.repository.PassPredictionRepository;
import com.sattrack.repository.UserAlertRepository;
import com.sattrack.service.ConjunctionService;
import com.sattrack.service.NotificationService;
import com.sattrack.service.PassPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackingScheduler {

    private final ConjunctionService       conjunctionService;
    private final PassPredictionService    passPredictionService;
    private final NotificationService      notificationService;
    private final UserAlertRepository      userAlertRepo;
    private final PassPredictionRepository passRepo;
    private final NotificationRepository   notifRepo;

    // Conjunction screening — every 6 hours

    @Scheduled(cron = "0 0 */6 * * *")
    public void runConjunctionScreening() {
        log.info("[SCHEDULER] Starting conjunction screening");
        try {
            int found = conjunctionService.screenAll();
            log.info("[SCHEDULER] Conjunction screening done — {} alerts", found);
        } catch (Exception ex) {
            log.error("[SCHEDULER] Conjunction screening failed", ex);
        }
    }

    // Pass alert dispatcher — every 5 minutes
    // Check if any user's watched satellite will pass within their lead time

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void dispatchPassAlerts() {
        List<UserAlert> alerts = userAlertRepo.findAllActiveByType(AlertType.PASS_OVERHEAD);
        if (alerts.isEmpty()) return;

        Instant now = Instant.now();

        for (UserAlert alert : alerts) {
            try {
                int lead = alert.getLeadTimeMinutes() > 0 ? alert.getLeadTimeMinutes() : 10;
                Instant windowStart = now;
                Instant windowEnd   = now.plus(lead + 5L, ChronoUnit.MINUTES);

                List<PassPrediction> upcoming = passRepo.findUpcomingPasses(
                        alert.getNoradId(),
                        getUserLat(alert.getUserId()),
                        getUserLon(alert.getUserId()),
                        windowStart,
                        windowEnd);

                for (PassPrediction pass : upcoming) {
                    // Only alert if AOS is within lead time window
                    long minutesUntilAos = ChronoUnit.MINUTES.between(now, pass.getAos());
                    if (minutesUntilAos > lead) continue;
                    if (alert.isVisibleOnly() && !pass.isVisible()) continue;
                    if (alert.getMinElevation() != null
                            && pass.getMaxElevation() < alert.getMinElevation()) continue;

                    // Avoid double-firing: don't notify if we already did within 30 min
                    if (alert.getLastTriggeredAt() != null &&
                            alert.getLastTriggeredAt().isAfter(now.minus(30, ChronoUnit.MINUTES))) continue;

                    notificationService.sendPassAlert(alert, pass);
                    log.info("[SCHEDULER] Pass alert sent: user={} sat={} aos={}",
                            alert.getUserId(), alert.getNoradId(), pass.getAos());
                }
            } catch (Exception ex) {
                log.warn("[SCHEDULER] Pass alert dispatch failed for alert {}: {}",
                        alert.getId(), ex.getMessage());
            }
        }
    }

    // Pre-compute pass predictions for all watched satellites — daily at 01:00


    @Scheduled(cron = "0 0 1 * * *")
    public void precomputePassPredictions() {
        log.info("[SCHEDULER] Pre-computing pass predictions for all user alerts");
        List<UserAlert> alerts = userAlertRepo.findAllActiveByType(AlertType.PASS_OVERHEAD);

        for (UserAlert alert : alerts) {
            try {
                PassRequest req = PassRequest.builder()
                        .noradId(alert.getNoradId())
                        .observerLat(getUserLat(alert.getUserId()))
                        .observerLon(getUserLon(alert.getUserId()))
                        .observerAltMeters(0)
                        .days(7)
                        .minElevation(alert.getMinElevation() != null ? alert.getMinElevation() : 10.0)
                        .visibleOnly(alert.isVisibleOnly())
                        .build();

                passPredictionService.predictPasses(req, alert.getUserId());
            } catch (Exception ex) {
                log.warn("[SCHEDULER] Pre-compute failed for user={} sat={}: {}",
                        alert.getUserId(), alert.getNoradId(), ex.getMessage());
            }
        }
        log.info("[SCHEDULER] Pre-compute complete for {} alerts", alerts.size());
    }

    // Housekeeping — daily at 03:00

    @Scheduled(cron = "0 0 3 * * *")
    public void runHousekeeping() {
        Instant cutoff30d  = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant cutoff90d  = Instant.now().minus(90, ChronoUnit.DAYS);

        int expiredPasses  = passRepo.deleteExpiredPasses(cutoff30d);
        int oldConj        = 0; // conjRepo.deleteOlderThan(cutoff30d) — call directly if needed
        int oldNotifs      = notifRepo.deleteOlderThan(cutoff90d);

        log.info("[SCHEDULER] Housekeeping: deleted {} expired passes, {} old notifications",
                expiredPasses, oldNotifs);
    }

    // Helpers — fetch user observer location from user record
    // In a real app, inject UserRepository and load from DB

    private double getUserLat(Long userId) {
        // TODO: inject UserRepository and load user.getDefaultLatitude()
        // Placeholder returns 0.0 — replace with real lookup
        return 0.0;
    }

    private double getUserLon(Long userId) {
        // TODO: inject UserRepository and load user.getDefaultLongitude()
        return 0.0;
    }
}