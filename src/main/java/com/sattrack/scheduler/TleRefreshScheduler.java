package com.sattrack.scheduler;

import com.sattrack.controller.SatelliteWebSocketController;
import com.sattrack.repository.TleRepository;
import com.sattrack.service.TleFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled TLE refresh and maintenance tasks.
 *
 * ⚠️ Production note:
 * This application uses an ON-DEMAND TLE strategy.
 * The scheduled refresh is kept ONLY for:
 *  - future batch refresh enablement
 *  - clearing WebSocket no-TLE cache
 *  - historical TLE maintenance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TleRefreshScheduler {

    private final TleFetcherService tleFetcherService;
    private final TleRepository tleRepository;
    private final SatelliteWebSocketController wsController;

    /**
     * Runs every 6 hours.
     *
     * Current behavior:
     * - Does NOT fetch external APIs aggressively
     * - Keeps scheduler wiring intact
     * - Clears WebSocket no-TLE cache
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 6 * 60 * 60 * 1000)
    public void refreshTles() {
        log.info("=== Scheduled TLE maintenance cycle ===");
        try {
            // Safe no-op unless batch refresh is enabled later
            tleFetcherService.refreshAllTles();

            // Allow WebSocket broadcaster to retry satellites
            wsController.onTleRefreshComplete();

        } catch (Exception e) {
            log.error("Scheduled TLE maintenance failed", e);
            // Never rethrow — scheduler must stay alive
        }
    }

    /**
     * Prune old TLE history.
     * Keeps last N epochs per satellite (default: 10).
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void pruneOldTles() {
        log.info("Starting TLE history pruning");
        try {
            int deleted = tleRepository.pruneOldTles(10);
            log.info("Pruned {} old TLE records", deleted);
        } catch (Exception e) {
            log.error("TLE pruning failed", e);
        }
    }
}