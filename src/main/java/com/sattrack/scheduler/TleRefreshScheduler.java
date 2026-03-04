package com.sattrack.scheduler;

import com.sattrack.controller.SatelliteWebSocketController;
import com.sattrack.repository.TleRepository;
import com.sattrack.service.TleFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled TLE maintenance tasks.
 *
 * IMPORTANT DESIGN NOTE
 * ----------------------------------
 * This system uses an ON-DEMAND TLE strategy.
 *
 * Scheduler is SAFE by design:
 *  - No aggressive external API calls
 *  - Clears WebSocket no-TLE suppression cache
 *  - Prunes old historical data
 *  -Keeps batch-refresh wiring intact
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TleRefreshScheduler {

    private final TleFetcherService tleFetcherService;
    private final TleRepository tleRepository;
    private final SatelliteWebSocketController wsController;

    /**
     * Maintenance cycle — every 6 hours.
     */
    @Scheduled(
            initialDelayString = "PT30S",
            fixedDelayString = "PT6H"
    )
    public void maintenanceCycle() {
        log.info("=== Scheduled TLE maintenance cycle ===");
        try {
            // Explicit no-op unless bulk refresh is enabled
            tleFetcherService.refreshAllTles();

            // Allow WebSocket broadcaster to retry satellites
            wsController.onTleRefreshComplete();

        } catch (Exception e) {
            // NEVER rethrow — scheduler must survive forever
            log.error("Scheduled TLE maintenance failed", e);
        }
    }

    /**
     * Prune historical TLE records.
     * Keeps last N epochs per satellite.
     * Runs daily at 03:00 UTC.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void pruneOldTles() {
        log.info("Starting TLE history pruning");
        try {
            int deleted = tleRepository.pruneOldTles(10);
            log.info("TLE pruning completed — {} records removed", deleted);
        } catch (Exception e) {
            log.error("TLE pruning failed", e);
        }
    }
}