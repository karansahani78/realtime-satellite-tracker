package com.sattrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sattrack.entity.Satellite;
import com.sattrack.entity.TleRecord;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.TleRepository;
import com.sattrack.util.TleElements;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TleFetcherService {

    private final WebClient webClient;
    private final SatelliteRepository satelliteRepository;
    private final TleRepository tleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Self-reference injected lazily so that calls to persistTles()
     * go through the Spring AOP proxy — making @Transactional(REQUIRES_NEW)
     * actually create a new independent transaction per batch.
     * Without this, self-calls (this.persistTles) bypass the proxy
     * and all inserts share one abortable transaction.
     */
    @Autowired
    @Lazy
    private TleFetcherService self;

    /* ===================== CONFIG ===================== */

    @Value("${space-track.username:}")
    private String spaceTrackUsername;

    @Value("${space-track.password:}")
    private String spaceTrackPassword;

    @Value("${n2yo.api-key:}")
    private String n2yoApiKey;

    private volatile boolean spaceTrackDisabled = false;

    /* ===================== ENDPOINTS ===================== */

    private static final String N2YO_TLE =
            "https://api.n2yo.com/rest/v1/satellite/tle/%s?apiKey=%s";

    private static final String SPACE_TRACK_LOGIN =
            "https://www.space-track.org/ajaxauth/login";

    private static final String SPACE_TRACK_TLE =
            "https://www.space-track.org/basicspacedata/query" +
                    "/class/tle_latest/NORAD_CAT_ID/%s/format/tle";

    private static final String CELESTRAK_SINGLE =
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=%s&FORMAT=TLE";

    /* ===================== STARTUP LOG ===================== */

    @PostConstruct
    void logProviders() {
        log.info("TLE providers enabled → N2YO: {}, Space-Track: {}, CelesTrak: always-fallback",
                isEnabled(n2yoApiKey), isEnabled(spaceTrackUsername));
    }

    private boolean isEnabled(String v) {
        return v != null && !v.isBlank();
    }

    /* ===================== ON-DEMAND FETCH ===================== */

    public boolean fetchSingleSatellite(String noradId) {
        return fetchFromN2yo(noradId)
                || fetchFromSpaceTrack(noradId)
                || fetchFromCelestrakSingle(noradId);
    }

    /* ===================== N2YO ===================== */

    private boolean fetchFromN2yo(String noradId) {
        if (!isEnabled(n2yoApiKey)) return false;
        try {
            String response = webClient.get()
                    .uri(String.format(N2YO_TLE, noradId, n2yoApiKey))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode tleNode = objectMapper.readTree(response).path("tle");
            if (tleNode.isMissingNode() || tleNode.asText().isBlank()) return false;

            int saved = self.persistTles(parse(tleNode.asText()), "n2yo");
            if (saved > 0) log.info("N2YO: fetched TLE for NORAD {}", noradId);
            return saved > 0;

        } catch (Exception e) {
            log.debug("N2YO failed for {}: {}", noradId, e.getMessage());
            return false;
        }
    }

    /* ===================== SPACE-TRACK ===================== */

    private boolean fetchFromSpaceTrack(String noradId) {
        if (spaceTrackDisabled || !isEnabled(spaceTrackUsername)) return false;
        try {
            String cookie = authenticateSpaceTrack();
            if (cookie == null) {
                spaceTrackDisabled = true;
                return false;
            }

            String raw = webClient.get()
                    .uri(String.format(SPACE_TRACK_TLE, noradId))
                    .header(HttpHeaders.COOKIE, cookie)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            int saved = self.persistTles(parse(raw), "space-track");
            return saved > 0;

        } catch (Exception e) {
            spaceTrackDisabled = true;
            log.warn("Space-Track disabled after error: {}", e.getMessage());
            return false;
        }
    }

    private String authenticateSpaceTrack() {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("identity", spaceTrackUsername);
            form.add("password", spaceTrackPassword);

            return webClient.post()
                    .uri(SPACE_TRACK_LOGIN)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .toBodilessEntity()
                    .map(r -> r.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                    .block();
        } catch (Exception e) {
            log.debug("Space-Track login failed: {}", e.getMessage());
            return null;
        }
    }

    /* ===================== CELESTRAK (SINGLE / FALLBACK) ===================== */

    private boolean fetchFromCelestrakSingle(String noradId) {
        try {
            String raw = webClient.get()
                    .uri(String.format(CELESTRAK_SINGLE, noradId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            int saved = self.persistTles(parse(raw), "celestrak-single");
            if (saved > 0) log.info("CelesTrak: fetched TLE for NORAD {}", noradId);
            return saved > 0;

        } catch (Exception e) {
            log.debug("CelesTrak failed for {}: {}", noradId, e.getMessage());
            return false;
        }
    }

    /* ===================== BULK LOAD (LOCAL FILES) ===================== */

    /**
     * NOT @Transactional — each file is persisted via self.persistTles()
     * which opens its own REQUIRES_NEW transaction through the proxy.
     * If this method were @Transactional, self-calls inside loadFromClasspath
     * would still join that outer transaction, defeating REQUIRES_NEW.
     */
    public void refreshAllTles() {
        log.info("=== Bulk TLE refresh started (LOCAL FILES) ===");
        // Bulk Loading
        // active.txt LAST — fills remaining satellites without overwriting named ones.
        loadFromClasspath("tle/starlink.txt", "Starlink");
        loadFromClasspath("tle/oneweb.txt",   "OneWeb");
        loadFromClasspath("tle/weather.txt",  "Weather");
        loadFromClasspath("tle/active.txt",   "active");
        // ──────────────────────────────────────────────────────────────────────

        long satCount = satelliteRepository.count();
        long tleCount = tleRepository.count();
        log.info("=== Bulk TLE refresh completed — satellites: {}, tle_records: {} ===",
                satCount, tleCount);
    }

    private void loadFromClasspath(String path, String source) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("TLE file not found in classpath: {} (place it at src/main/resources/{})",
                        path, path);
                return;
            }

            String raw;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                raw = reader.lines().collect(Collectors.joining("\n"));
            }

            List<TleRaw> parsed = parse(raw);
            log.info("[{}] parsed {} TLE triplets from {}", source, parsed.size(), path);

            if (parsed.isEmpty()) {
                log.warn("[{}] No valid TLE triplets found — check file format", source);
                return;
            }

            // Chunk into batches of 200 — each chunk is its own REQUIRES_NEW transaction.
            // This way a single bad record can't abort thousands of good ones.
            int chunkSize = 200;
            int totalSaved = 0;
            for (int i = 0; i < parsed.size(); i += chunkSize) {
                List<TleRaw> chunk = parsed.subList(i, Math.min(i + chunkSize, parsed.size()));
                try {
                    totalSaved += self.persistTles(chunk, source);
                } catch (Exception e) {
                    log.warn("[{}] Chunk {}-{} failed: {}", source, i, i + chunkSize, e.getMessage());
                }
            }
            log.info("[{}] saved {} new satellites/TLEs", source, totalSaved);

        } catch (Exception e) {
            log.error("Failed loading {}: {}", path, e.getMessage(), e);
        }
    }

    /* ===================== PARSER ===================== */

    List<TleRaw> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        String[] lines = raw.replace("\r", "").split("\n");
        List<TleRaw> list = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < lines.length - 2; i++) {
            String l1 = lines[i + 1].trim();
            String l2 = lines[i + 2].trim();

            if (l1.startsWith("1 ") && l1.length() >= 69
                    && l2.startsWith("2 ") && l2.length() >= 69) {

                // Sanitize name: take only the part before any comma
                String name = lines[i].trim().split(",")[0].trim();

                list.add(new TleRaw(name, l1, l2));
                i += 2; // skip consumed lines
            } else {
                skipped++;
            }
        }

        if (skipped > 0) {
            log.debug("Parser skipped {} lines that were not valid TLE triplets", skipped);
        }
        return list;
    }

    /* ===================== PERSIST (ALWAYS IN OWN TRANSACTION) ===================== */

    /**
     * REQUIRES_NEW: opens a brand-new transaction independent of any caller.
     * Must be called via self.persistTles() — NOT this.persistTles() —
     * otherwise Spring proxy is bypassed and this annotation is ignored.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persistTles(List<TleRaw> entries, String source) {
        int saved = 0;
        int skippedDuplicate = 0;
        int skippedError = 0;


        // True for the named category files: starlink, oneweb, weather.
        // These take priority over the generic "active" label.
        boolean isNamedCategory = !source.equals("active")
                && !source.equals("celestrak-single")
                && !source.equals("n2yo")
                && !source.equals("space-track");
        // ──────────────────────────────────────────────────────────────────────

        for (TleRaw e : entries) {
            try {
                TleElements el = TleElements.parse(e.line0(), e.line1(), e.line2());

                if (tleRepository.existsByNoradIdAndEpoch(el.noradId(), el.epoch())) {
                    skippedDuplicate++;

                    // TLE is a duplicate but the satellite category may still
                    // need promoting (e.g. on a second run against existing data).
                    if (isNamedCategory) {
                        satelliteRepository.findByNoradId(el.noradId()).ifPresent(sat -> {
                            boolean currentlyGeneric = "active".equals(sat.getCategory())
                                    || sat.getCategory() == null;
                            if (currentlyGeneric) {
                                sat.setCategory(source);
                                satelliteRepository.save(sat);
                            }
                        });
                    }
                    // ────────────────────────────────────────────────────────────
                    continue;
                }

                // Original code used orElseGet which never updated an existing sat.
                // New code: find → create-or-update with category awareness.
                Satellite sat = satelliteRepository
                        .findByNoradId(el.noradId())
                        .orElse(null);

                if (sat == null) {
                    // New satellite — always save with current source as category.
                    sat = satelliteRepository.save(
                            Satellite.builder()
                                    .noradId(el.noradId())
                                    .name(el.name() == null || el.name().isBlank()
                                            ? "SAT-" + el.noradId()
                                            : el.name())
                                    .category(source)
                                    .active(true)
                                    .build()
                    );
                } else {
                    // Existing satellite: promote to named category only if it
                    // currently holds the generic "active" (or null) label.
                    // This prevents active.txt from overwriting "starlink" → "active".
                    boolean currentlyGeneric = "active".equals(sat.getCategory())
                            || sat.getCategory() == null;
                    if (isNamedCategory && currentlyGeneric) {
                        sat.setCategory(source);
                        satelliteRepository.save(sat);
                    }
                }
                // ────────────────────────────────────────────────────────────────

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
                skippedError++;
                log.debug("[{}] Skipped TLE entry due to error: {}", source, ex.getMessage());
            }
        }

        if (skippedDuplicate > 0 || skippedError > 0) {
            log.debug("[{}] persistTles — saved: {}, duplicates skipped: {}, errors skipped: {}",
                    source, saved, skippedDuplicate, skippedError);
        }
        return saved;
    }

    record TleRaw(String line0, String line1, String line2) {}
}