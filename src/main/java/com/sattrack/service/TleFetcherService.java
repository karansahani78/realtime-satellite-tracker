package com.sattrack.service;

import com.sattrack.entity.Satellite;
import com.sattrack.entity.TleRecord;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.TleRepository;
import com.sattrack.util.TleElements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches live TLE data from CelesTrak GP (General Perturbations) data API.
 *
 * WHY the URL changed from the old /pub/TLE/*.txt paths:
 * CelesTrak migrated to a query-based GP data API in 2023-2024.
 * The old static file paths now return 403 Forbidden.
 *
 * Correct 2024+ endpoints:
 *   Group:  https://celestrak.org/SOCRATES/query.php?GROUP=stations&FORMAT=TLE
 *   Single: https://celestrak.org/SOCRATES/query.php?CATNR=25544&FORMAT=TLE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TleFetcherService {

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;
    private final TleRepository tleRepository;

    // CelesTrak GP data API — confirmed working 2024
    private static final String GP_GROUP_URL = "https://celestrak.org/SOCRATES/query.php?GROUP=%s&FORMAT=TLE";
    private static final String GP_CATNR_URL = "https://celestrak.org/SOCRATES/query.php?CATNR=%s&FORMAT=TLE";

    /**
     * GROUP parameter values from CelesTrak's current API.
     * Verified at https://celestrak.org/SOCRATES/
     */
    private static final List<TleGroup> TLE_GROUPS = List.of(
        new TleGroup("stations",  "ISS & Stations"),
        new TleGroup("starlink",  "Starlink"),
        new TleGroup("gps-ops",   "GPS"),
        new TleGroup("glo-ops",   "GLONASS"),
        new TleGroup("galileo",   "Galileo"),
        new TleGroup("weather",   "Weather"),
        new TleGroup("amateur",   "Amateur"),
        new TleGroup("science",   "Science"),
        new TleGroup("military",  "Military")
    );

    /**
     * Fetch all TLE groups from CelesTrak and persist new records.
     * Called by the scheduler every 6 hours.
     */
    @Transactional
    public void refreshAllTles() {
        log.info("Starting TLE refresh for {} groups", TLE_GROUPS.size());
        int totalFetched = 0;
        int totalNew = 0;

        for (TleGroup group : TLE_GROUPS) {
            try {
                FetchResult result = fetchGroup(group);
                totalFetched += result.fetched();
                totalNew += result.newRecords();
                log.info("Group '{}': fetched={}, new={}", group.name(), result.fetched(), result.newRecords());
                // Small delay to be polite to CelesTrak's servers
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to fetch TLE group '{}': {}", group.name(), e.getMessage());
            }
        }

        log.info("TLE refresh complete. Total fetched={}, new={}", totalFetched, totalNew);
    }

    /**
     * Fetch TLE for a single satellite by NORAD ID (on-demand).
     * Uses CelesTrak GP CATNR API: ?CATNR=<id>&FORMAT=TLE
     */
    @Transactional
    public boolean fetchSingleSatellite(String noradId) {
        String url = String.format(GP_CATNR_URL, noradId);
        try {
            String rawTle = webClient.get()
                    .uri(url)
                    .header("Accept", "text/plain")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorResume(e -> {
                        log.warn("On-demand TLE fetch failed for NORAD {}: {}", noradId, e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (rawTle == null || rawTle.isBlank() || rawTle.contains("No GP data found")) {
                return false;
            }

            List<TleRawEntry> entries = parseTleText(rawTle);
            if (entries.isEmpty()) return false;

            int saved = persistTleEntries(entries, "celestrak-single");
            log.info("On-demand fetch for NORAD {}: {} TLE record(s) saved", noradId, saved);
            return saved > 0;
        } catch (Exception e) {
            log.warn("Failed to fetch single satellite TLE for NORAD {}: {}", noradId, e.getMessage());
            return false;
        }
    }

    private FetchResult fetchGroup(TleGroup group) {
        String url = String.format(GP_GROUP_URL, group.name());

        String rawTle = webClient.get()
                .uri(url)
                .header("Accept", "text/plain")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("CelesTrak request failed for group '{}': {}", group.name(), e.getMessage());
                    return Mono.empty();
                })
                .block();

        if (rawTle == null || rawTle.isBlank() || rawTle.contains("No GP data found")) {
            return new FetchResult(0, 0);
        }

        List<TleRawEntry> entries = parseTleText(rawTle);
        int newCount = persistTleEntries(entries, group.category());
        return new FetchResult(entries.size(), newCount);
    }

    /**
     * Parse raw TLE text (3-line format) into entry list.
     * Robust to Windows/Unix line endings and trailing whitespace.
     */
    List<TleRawEntry> parseTleText(String rawTle) {
        String[] lines = rawTle.replaceAll("\r\n", "\n").replaceAll("\r", "\n").split("\n");

        List<TleRawEntry> entries = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();

            // Find TLE line 1 (starts with "1 " and is at least 69 chars)
            if (line.startsWith("1 ") && line.length() >= 69) {
                // Previous non-TLE line is the satellite name
                String name = (i > 0
                        && !lines[i - 1].trim().startsWith("1 ")
                        && !lines[i - 1].trim().startsWith("2 "))
                        ? lines[i - 1].trim() : null;

                // Next line should be TLE line 2
                if (i + 1 < lines.length) {
                    String line2 = lines[i + 1].trim();
                    if (line2.startsWith("2 ") && line2.length() >= 69) {
                        entries.add(new TleRawEntry(name, line, line2));
                        i += 2;
                        continue;
                    }
                }
            }
            i++;
        }
        return entries;
    }

    private int persistTleEntries(List<TleRawEntry> entries, String source) {
        int newCount = 0;
        for (TleRawEntry entry : entries) {
            try {
                TleElements elements = TleElements.parse(entry.line0(), entry.line1(), entry.line2());

                // Idempotent: skip exact duplicate epochs
                if (tleRepository.existsByNoradIdAndEpoch(elements.noradId(), elements.epoch())) {
                    continue;
                }

                // Upsert satellite metadata
                Satellite satellite = satelliteRepository.findByNoradId(elements.noradId())
                        .orElseGet(() -> satelliteRepository.save(
                            Satellite.builder()
                                .noradId(elements.noradId())
                                .name(elements.name() != null ? elements.name() : "OBJECT " + elements.noradId())
                                .category(source)
                                .active(true)
                                .build()
                        ));

                tleRepository.save(TleRecord.builder()
                        .satellite(satellite)
                        .noradId(elements.noradId())
                        .line0(entry.line0())
                        .line1(entry.line1())
                        .line2(entry.line2())
                        .epoch(elements.epoch())
                        .source(source)
                        .build());
                newCount++;
            } catch (Exception e) {
                log.debug("Failed to parse/persist TLE entry: {}", e.getMessage());
            }
        }
        return newCount;
    }

    // --- Supporting types ---
    private record TleGroup(String name, String category) {}
    private record TleRawEntry(String line0, String line1, String line2) {}
    private record FetchResult(int fetched, int newRecords) {}
}
