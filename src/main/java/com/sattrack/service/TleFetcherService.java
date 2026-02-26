package com.sattrack.service;

import com.sattrack.entity.Satellite;
import com.sattrack.entity.TleRecord;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.TleRepository;
import com.sattrack.util.TleElements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TleFetcherService {

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;
    private final TleRepository tleRepository;

    @Value("${space-track.username:}")
    private String username;

    @Value("${space-track.password:}")
    private String password;

    /* ============================================================
       ENDPOINTS
       ============================================================ */

    // NOTE: CelesTrak is currently unreachable on some networks.
    // Kept here for future use or different deployment environments.
    private static final String CELESTRAK_TLE =
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=%s&FORMAT=TLE";

    private static final String SPACE_TRACK_LOGIN =
            "https://www.space-track.org/ajaxauth/login";

    // ✅ FIXED: Use tle_latest (old /class/tle endpoint returns 404)
    private static final String SPACE_TRACK_TLE =
            "https://www.space-track.org/basicspacedata/query" +
                    "/class/tle_latest/NORAD_CAT_ID/%s/format/tle";

    /* ============================================================
       SCHEDULER HOOK
       ============================================================ */

    @Transactional
    public void refreshAllTles() {
        // Intentionally no-op
        log.info("Scheduled TLE refresh skipped (on-demand strategy enabled)");
    }

    /* ============================================================
       PUBLIC API
       ============================================================ */

    /**
     * NOT transactional:
     * HTTP calls must never hold DB connections.
     */
    public boolean fetchSingleSatellite(String noradId) {
        // CelesTrak first (if reachable)
        if (fetchFromCelestrak(noradId)) {
            return true;
        }
        // Reliable fallback
        return fetchFromSpaceTrack(noradId);
    }

    /* ============================================================
       CELESTRAK (OPTIONAL)
       ============================================================ */

    private boolean fetchFromCelestrak(String noradId) {
        try {
            String raw = webClient.get()
                    .uri(String.format(CELESTRAK_TLE, noradId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (raw == null || raw.isBlank()) return false;

            int saved = persistTles(parse(raw), "celestrak");

            if (saved > 0) {
                log.info("Fetched TLE for {} from CelesTrak", noradId);
                return true;
            }
        } catch (Exception e) {
            log.debug("CelesTrak failed for {}: {}", noradId, e.getMessage());
        }
        return false;
    }

    /* ============================================================
       SPACE-TRACK (PRIMARY / RELIABLE)
       ============================================================ */

    private boolean fetchFromSpaceTrack(String noradId) {
        if (username == null || username.isBlank()) {
            return false;
        }

        try {
            String cookie = authenticate();
            if (cookie == null) return false;

            String raw = webClient.get()
                    .uri(String.format(SPACE_TRACK_TLE, noradId))
                    .header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(40))
                    .block();

            if (raw == null || raw.isBlank()) return false;

            int saved = persistTles(parse(raw), "space-track");

            if (saved > 0) {
                log.info("Fetched TLE for {} from Space-Track", noradId);
                return true;
            }

        } catch (Exception e) {
            log.debug("Space-Track failed for {}: {}", noradId, e.getMessage());
        }
        return false;
    }

    private String authenticate() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("identity", username);
            form.add("password", password);

            return webClient.post()
                    .uri(SPACE_TRACK_LOGIN)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .toBodilessEntity()
                    .map(resp -> resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                    .block();

        } catch (Exception e) {
            log.debug("Space-Track login failed: {}", e.getMessage());
            return null;
        }
    }

    /* ============================================================
       PARSING
       ============================================================ */

    List<TleRaw> parse(String raw) {
        String[] lines = raw.replace("\r", "").split("\n");
        List<TleRaw> list = new ArrayList<>();

        for (int i = 0; i + 2 < lines.length; i++) {
            if (lines[i + 1].startsWith("1 ")
                    && lines[i + 2].startsWith("2 ")) {
                list.add(new TleRaw(
                        lines[i].trim(),
                        lines[i + 1].trim(),
                        lines[i + 2].trim()
                ));
                i += 2;
            }
        }
        return list;
    }

    /* ============================================================
       PERSISTENCE
       ============================================================ */

    /**
     * Always uses its own write transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persistTles(List<TleRaw> entries, String source) {
        int saved = 0;

        for (TleRaw e : entries) {
            try {
                TleElements el = TleElements.parse(
                        e.line0(), e.line1(), e.line2());

                if (tleRepository.existsByNoradIdAndEpoch(
                        el.noradId(), el.epoch())) {
                    continue;
                }

                Satellite sat = satelliteRepository
                        .findByNoradId(el.noradId())
                        .orElseGet(() -> satelliteRepository.save(
                                Satellite.builder()
                                        .noradId(el.noradId())
                                        .name(el.name())
                                        .category(source)
                                        .active(true)
                                        .build()
                        ));

                tleRepository.save(TleRecord.builder()
                        .satellite(sat)
                        .noradId(el.noradId())
                        .line0(e.line0())
                        .line1(e.line1())
                        .line2(e.line2())
                        .epoch(el.epoch())
                        .source(source)
                        .build());

                saved++;

            } catch (Exception ex) {
                log.warn("TLE persist failed: {}", ex.getMessage());
            }
        }
        return saved;
    }

    private record TleRaw(String line0, String line1, String line2) {}
}