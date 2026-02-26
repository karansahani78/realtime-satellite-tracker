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

    // ================== CELESTRAK (PRIMARY) ==================
    private static final String CELESTRAK_TLE =
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=%s&FORMAT=TLE";

    // ================== SPACE-TRACK (FALLBACK) ==================
    private static final String LOGIN_URL =
            "https://www.space-track.org/ajaxauth/login";

    private static final String SPACE_TRACK_TLE =
            "https://www.space-track.org/basicspacedata/query" +
                    "/class/tle/NORAD_CAT_ID/%s/orderby/EPOCH desc/limit/1/format/tle";

    /* ============================================================
       SCHEDULER COMPATIBILITY (🔥 FIXES YOUR COMPILATION ERROR)
       ============================================================ */

    /**
     * Required by TleRefreshScheduler.
     * Production strategy = on-demand fetch only.
     */
    @Transactional
    public void refreshAllTles() {
        log.info("Scheduled TLE refresh skipped (on-demand strategy enabled)");
    }

    /* ============================================================
       PUBLIC API
       ============================================================ */

    /**
     * Fetch TLE for one satellite (on-demand).
     */
    @Transactional
    public boolean fetchSingleSatellite(String noradId) {

        // 1️⃣ Try CelesTrak first (no auth, fastest)
        if (fetchFromCelestrak(noradId)) {
            return true;
        }

        // 2️⃣ Fallback to Space-Track (only if creds exist)
        return fetchFromSpaceTrack(noradId);
    }

    /* ============================================================
       CELESTRAK (PRIMARY)
       ============================================================ */

    private boolean fetchFromCelestrak(String noradId) {
        try {
            String raw = webClient.get()
                    .uri(String.format(CELESTRAK_TLE, noradId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (raw == null || raw.isBlank()) return false;

            List<TleRaw> parsed = parse(raw);
            int saved = persist(parsed, "celestrak");

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
       SPACE-TRACK (FALLBACK ONLY)
       ============================================================ */

    private boolean fetchFromSpaceTrack(String noradId) {
        if (username == null || username.isBlank()) return false;

        try {
            String cookie = authenticate();
            if (cookie == null) return false;

            String raw = webClient.get()
                    .uri(String.format(SPACE_TRACK_TLE, noradId))
                    .header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (raw == null || raw.isBlank()) return false;

            List<TleRaw> parsed = parse(raw);
            return persist(parsed, "space-track") > 0;

        } catch (Exception e) {
            log.debug("Space-Track failed for {}: {}", noradId, e.getMessage());
            return false;
        }
    }

    private String authenticate() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("identity", username);
            form.add("password", password);

            return webClient.post()
                    .uri(LOGIN_URL)
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
       PARSING & STORAGE
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

    private int persist(List<TleRaw> entries, String source) {
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
                log.debug("TLE persist failed: {}", ex.getMessage());
            }
        }
        return saved;
    }

    private record TleRaw(String line0, String line1, String line2) {}
}