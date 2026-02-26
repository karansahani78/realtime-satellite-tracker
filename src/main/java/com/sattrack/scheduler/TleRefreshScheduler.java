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
 * TLE refresh every 6 hours matches CelesTrak's update frequency.
 * Initial delay of 30s allows the DB/connection pool to fully start
 * before making external HTTP calls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TleRefreshScheduler {

    private final TleFetcherService tleFetcherService;
    private final TleRepository tleRepository;
    private final SatelliteWebSocketController wsController;

    /**
     * Refresh all TLE groups every 6 hours.
     * Initial delay: 30s (let app fully start before hitting external APIs).
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 6 * 60 * 60 * 1000)
    public void refreshTles() {
        log.info("=== Scheduled TLE refresh starting ===");
        try {
            tleFetcherService.refreshAllTles();
            // Notify WebSocket broadcaster that new TLEs may now be available
            wsController.onTleRefreshComplete();
        } catch (Exception e) {
            log.error("Scheduled TLE refresh failed", e);
            // Don't rethrow — scheduler must continue running for next cycle
        }
    }

    /**
     * Prune old TLE records daily at 3 AM UTC.
     * Keeps last 10 TLE epochs per satellite for historical replay.
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
