package com.sattrack.controller;

import com.sattrack.dto.SatelliteDto;
import com.sattrack.repository.TleRepository;
import com.sattrack.service.OrbitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket controller that pushes satellite positions on a schedule.
 *
 * Connected clients subscribe to:
 *   /topic/satellites/{noradId}  – single satellite real-time position
 *
 * Broadcast runs every 5 seconds but only for satellites that have TLE data.
 * Uses a failed-IDs set to suppress repeated log noise when TLE data hasn't
 * loaded yet — the scheduler re-checks after each 6-hour TLE refresh cycle.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SatelliteWebSocketController {

    private final OrbitService orbitService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TleRepository tleRepository;

    // IDs of satellites to broadcast on the default real-time feed
    private static final List<String> FEATURED_SATELLITES = List.of(
            "25544",  // ISS (ZARYA)
            "20580",  // Hubble Space Telescope
            "43226",  // CSS (Tianhe)
            "33591",  // NOAA 19
            "28654"   // NOAA 18
    );

    // Track which IDs have no TLE so we don't log an error every 5 seconds
    private final Set<String> noTleIds = ConcurrentHashMap.newKeySet();

    /**
     * Client sends to /app/track/{noradId}
     * Server replies to  /topic/satellites/{noradId}
     */
    @MessageMapping("/track/{noradId}")
    @SendTo("/topic/satellites/{noradId}")
    public SatelliteDto.SatellitePosition handleTrackRequest(
            @DestinationVariable String noradId) {
        try {
            return orbitService.getCurrentPosition(noradId, null, null);
        } catch (Exception e) {
            log.warn("WS track request failed for {}: {}", noradId, e.getMessage());
            return null;
        }
    }

    /**
     * Broadcast featured satellite positions every 5 seconds.
     * Silently skips satellites with no TLE data yet (avoids log spam on startup).
     * Once a TLE becomes available the noTleIds set is cleared by the refresh scheduler.
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastFeaturedSatellites() {
        for (String noradId : FEATURED_SATELLITES) {
            // Skip if we already know there's no TLE — don't spam logs
            if (noTleIds.contains(noradId)) continue;

            // Fast check: does a TLE exist in DB at all?
            if (tleRepository.findLatestByNoradId(noradId).isEmpty()) {
                noTleIds.add(noradId);
                log.debug("No TLE yet for NORAD {} — skipping broadcast until TLE refresh", noradId);
                continue;
            }

            try {
                SatelliteDto.SatellitePosition pos = orbitService.getCurrentPosition(noradId, null, null);
                messagingTemplate.convertAndSend("/topic/satellites/" + noradId, pos);
                // If we previously marked it as missing, clear it now
                noTleIds.remove(noradId);
            } catch (Exception e) {
                log.debug("Broadcast failed for {}: {}", noradId, e.getMessage());
            }
        }
    }

    /**
     * Called by TleRefreshScheduler after a successful refresh.
     * Clears the no-TLE cache so satellites get a fresh chance.
     */
    public void onTleRefreshComplete() {
        noTleIds.clear();
        log.debug("Cleared no-TLE cache after refresh");
    }
}
