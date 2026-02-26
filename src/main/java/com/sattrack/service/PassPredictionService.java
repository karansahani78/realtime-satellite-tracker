package com.sattrack.service;

import com.sattrack.dto.TrackingDto.PassRequest;
import com.sattrack.dto.TrackingDto.PassSummary;
import com.sattrack.entity.PassPrediction;
import com.sattrack.entity.TleRecord;
import com.sattrack.exception.SatelliteNotFoundException;
import com.sattrack.repository.PassPredictionRepository;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.TleRepository;
import com.sattrack.service.OrekitPropagator.LookAngles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassPredictionService {

    private static final int    STEP_SECONDS      = 15;    // propagation step
    private static final double DEFAULT_MIN_EL    = 10.0;  // degrees
    private static final int    DEFAULT_DAYS      = 7;
    private static final int    MAX_DAYS          = 10;

    private final TleRepository            tleRepository;
    private final SatelliteRepository      satelliteRepository;
    private final PassPredictionRepository passRepo;
    private final OrekitPropagator         propagator;
    private final NotificationService      notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public List<PassSummary> predictPasses(PassRequest req, Long userId) {
        int    days   = Math.min(req.getDays() > 0 ? req.getDays() : DEFAULT_DAYS, MAX_DAYS);
        double minEl  = req.getMinElevation() > 0 ? req.getMinElevation() : DEFAULT_MIN_EL;

        TleRecord tle = tleRepository.findLatestByNoradId(req.getNoradId())
                .orElseThrow(() -> new SatelliteNotFoundException(
                        "No TLE found for NORAD ID: " + req.getNoradId()));

        String satName = satelliteRepository.findByNoradId(req.getNoradId())
                .map(s -> s.getName()).orElse(req.getNoradId());

        Instant from = Instant.now();
        Instant to   = from.plus(days, ChronoUnit.DAYS);

        // Return cached predictions if available
        boolean cached = passRepo
                .existsByNoradIdAndObserverLatBetweenAndObserverLonBetweenAndAosAfter(
                        req.getNoradId(),
                        req.getObserverLat() - 0.05, req.getObserverLat() + 0.05,
                        req.getObserverLon() - 0.05, req.getObserverLon() + 0.05,
                        from);

        List<PassPrediction> predictions;
        if (cached) {
            predictions = passRepo.findUpcomingPasses(
                    req.getNoradId(), req.getObserverLat(), req.getObserverLon(), from, to);
        } else {
            predictions = computeAndPersist(tle, satName, req, from, to, userId, minEl);
        }

        return predictions.stream()
                .filter(p -> p.getMaxElevation() >= minEl)
                .filter(p -> !req.isVisibleOnly() || p.isVisible())
                .map(this::toSummary)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core computation
    // ─────────────────────────────────────────────────────────────────────────

    private List<PassPrediction> computeAndPersist(
            TleRecord tle, String satName, PassRequest req,
            Instant from, Instant to, Long userId, double minEl) {

        log.info("Computing passes for {} from {} to {} observer={},{}",
                tle.getNoradId(), from, to, req.getObserverLat(), req.getObserverLon());

        List<PassPrediction> results = new ArrayList<>();

        // State machine: track when we cross the horizon
        boolean inPass      = false;
        Instant aosTime     = null;
        double  aosAzimuth  = 0;
        double  maxEl       = 0;
        Instant tcaTime     = null;
        double  tcaAzimuth  = 0;
        LookAngles prevAngles = null;

        Instant cursor = from;

        while (cursor.isBefore(to)) {
            LookAngles angles;
            try {
                angles = propagator.lookAngles(tle, cursor,
                        req.getObserverLat(), req.getObserverLon(),
                        req.getObserverAltMeters());
            } catch (Exception ex) {
                log.debug("Propagation error at {}: {}", cursor, ex.getMessage());
                cursor = cursor.plusSeconds(STEP_SECONDS);
                continue;
            }

            double el = angles.elevationDeg();

            if (!inPass && el > 0) {
                // AOS — refine with binary search
                Instant refinedAos = el > 0 ? refineHorizonCrossing(tle, req,
                        cursor.minusSeconds(STEP_SECONDS), cursor, true) : cursor;
                aosTime    = refinedAos;
                aosAzimuth = angles.azimuthDeg();
                inPass     = true;
                maxEl      = el;
                tcaTime    = cursor;
                tcaAzimuth = angles.azimuthDeg();
            }

            if (inPass) {
                if (el > maxEl) {
                    maxEl      = el;
                    tcaTime    = cursor;
                    tcaAzimuth = angles.azimuthDeg();
                }

                if (el <= 0) {
                    // LOS — refine
                    Instant refinedLos = refineHorizonCrossing(tle, req,
                            cursor.minusSeconds(STEP_SECONDS), cursor, false);
                    LookAngles losAngles = angles;

                    long durationSec = aosTime != null
                            ? refinedLos.getEpochSecond() - aosTime.getEpochSecond() : 0;

                    if (maxEl >= minEl && durationSec > 0) {
                        boolean vis = isVisible(tle, tcaTime, req);

                        PassPrediction pass = PassPrediction.builder()
                                .noradId(tle.getNoradId())
                                .satelliteName(satName)
                                .observerLat(req.getObserverLat())
                                .observerLon(req.getObserverLon())
                                .observerAltMeters(req.getObserverAltMeters())
                                .aos(aosTime)
                                .tca(tcaTime)
                                .los(refinedLos)
                                .aosAzimuth(aosAzimuth)
                                .tcaAzimuth(tcaAzimuth)
                                .losAzimuth(losAngles.azimuthDeg())
                                .maxElevation(maxEl)
                                .durationSeconds(durationSec)
                                .visible(vis)
                                .computedAt(Instant.now())
                                .userId(userId)
                                .build();

                        results.add(passRepo.save(pass));
                    }

                    inPass = false; maxEl = 0; aosTime = null;
                }
            }

            prevAngles = angles;
            cursor = cursor.plusSeconds(STEP_SECONDS);
        }

        return results;
    }

    /**
     * Binary search to refine AOS/LOS to within ~1 second accuracy.
     */
    private Instant refineHorizonCrossing(TleRecord tle, PassRequest req,
                                          Instant before, Instant after,
                                          boolean findRising) {
        for (int i = 0; i < 8; i++) {   // 8 iterations → ~0.4 sec precision
            Instant mid = Instant.ofEpochSecond(
                    (before.getEpochSecond() + after.getEpochSecond()) / 2);
            try {
                LookAngles a = propagator.lookAngles(tle, mid,
                        req.getObserverLat(), req.getObserverLon(),
                        req.getObserverAltMeters());
                if (findRising) {
                    if (a.elevationDeg() > 0) after = mid; else before = mid;
                } else {
                    if (a.elevationDeg() > 0) before = mid; else after = mid;
                }
            } catch (Exception ignored) { break; }
        }
        return findRising ? after : before;
    }

    /**
     * A pass is "visible" if the observer is in night AND the satellite is in sunlight.
     * Simplified check using solar elevation angle heuristic.
     */
    private boolean isVisible(TleRecord tle, Instant at, PassRequest req) {
        // Full sun-position/shadow calculation would require SolarBodyPosition from Orekit.
        // For now, we use a simplified rule:
        // satellite altitude < 1000km (LEO) AND it's between civil twilight hours.
        // A proper implementation integrates SolarPosition.apparentElevation().
        try {
            var state = propagator.propagate(tle, at);
            return state.altKm() < 2000; // LEO satellites are potentially visible
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping
    // ─────────────────────────────────────────────────────────────────────────

    private PassSummary toSummary(PassPrediction p) {
        long dur = p.getDurationSeconds();
        String durLabel = String.format("%d min %02d sec", dur / 60, dur % 60);

        return PassSummary.builder()
                .id(p.getId())
                .noradId(p.getNoradId())
                .satelliteName(p.getSatelliteName())
                .aos(p.getAos())
                .tca(p.getTca())
                .los(p.getLos())
                .aosAzimuth(p.getAosAzimuth())
                .aosDirection(azimuthToCompass(p.getAosAzimuth()))
                .tcaAzimuth(p.getTcaAzimuth())
                .losAzimuth(p.getLosAzimuth())
                .losDirection(azimuthToCompass(p.getLosAzimuth()))
                .maxElevation(p.getMaxElevation())
                .durationSeconds(p.getDurationSeconds())
                .durationLabel(durLabel)
                .magnitude(p.getMagnitude())
                .visible(p.isVisible())
                .build();
    }

    private String azimuthToCompass(double az) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(az / 22.5) % 16];
    }
}