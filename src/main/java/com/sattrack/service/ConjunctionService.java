package com.sattrack.service;

import com.sattrack.dto.TrackingDto.ConjunctionSummary;
import com.sattrack.entity.ConjunctionAlert;
import com.sattrack.entity.ConjunctionAlert.RiskLevel;
import com.sattrack.entity.TleRecord;
import com.sattrack.repository.ConjunctionAlertRepository;
import com.sattrack.repository.TleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConjunctionService {

    /** Screen threshold — only run fine-grained search below this (km) */
    private static final double SCREEN_KM  = 50.0;
    /** Alert threshold — save to DB below this (km) */
    private static final double ALERT_KM   = 5.0;
    /** Coarse step (seconds) for initial scan */
    private static final int    COARSE_SEC = 60;
    /** Fine step (seconds) for TCA refinement */
    private static final int    FINE_SEC   = 5;
    /** Lookahead window (hours) */
    private static final int    LOOKAHEAD_H = 24;

    private final TleRepository            tleRepository;
    private final ConjunctionAlertRepository conjRepo;
    private final OrekitPropagator          propagator;
    private final NotificationService       notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Query
    // ─────────────────────────────────────────────────────────────────────────

    public Page<ConjunctionSummary> getUpcoming(int page, int size) {
        Instant now = Instant.now();
        return conjRepo.findUpcoming(now, now.plus(LOOKAHEAD_H, ChronoUnit.HOURS),
                        PageRequest.of(page, size, Sort.by("missDistanceKm")))
                .map(this::toSummary);
    }

    public List<ConjunctionSummary> getBySatellite(String noradId) {
        return conjRepo.findBySatellite(noradId, Instant.now())
                .stream().map(this::toSummary).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screening — called by scheduler
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public int screenAll() {
        List<String>    noradIds = tleRepository.findAllTrackedNoradIds();
        List<TleRecord> tles     = noradIds.stream()
                .map(id -> tleRepository.findLatestByNoradId(id).orElse(null))
                .filter(t -> t != null)
                .toList();

        log.info("Conjunction screening: {} satellites, {} pairs",
                tles.size(), (long) tles.size() * (tles.size() - 1) / 2);

        Instant now = Instant.now();
        Instant end = now.plus(LOOKAHEAD_H, ChronoUnit.HOURS);
        int saved   = 0;

        for (int i = 0; i < tles.size(); i++) {
            for (int j = i + 1; j < tles.size(); j++) {
                TleRecord a = tles.get(i);
                TleRecord b = tles.get(j);

                // Skip pairs in same orbit shell (same NORAD prefix) — usually safe
                // Coarse screen: check distance at T+0, T+1h, T+6h, T+12h, T+24h
                if (!coarseScreen(a, b, now, end)) continue;

                // Fine scan
                ConjunctionCandidate best = fineScan(a, b, now, end);
                if (best == null || best.distanceKm > ALERT_KM) continue;

                // Dedup — avoid re-saving same close approach within 6h window
                if (conjRepo.existsForPairInWindow(
                        a.getNoradId(), b.getNoradId(),
                        best.tca.minus(3, ChronoUnit.HOURS),
                        best.tca.plus(3, ChronoUnit.HOURS))) continue;

                RiskLevel risk = ConjunctionAlert.riskFrom(best.distanceKm);
                ConjunctionAlert alert = ConjunctionAlert.builder()
                        .noradIdA(a.getNoradId())
                        .satelliteNameA(a.getNoradId())   // enriched later if needed
                        .noradIdB(b.getNoradId())
                        .satelliteNameB(b.getNoradId())
                        .tca(best.tca)
                        .missDistanceKm(best.distanceKm)
                        .relativeSpeedKms(best.relSpeedKms)
                        .riskLevel(risk)
                        .computedAt(now)
                        .notificationSent(false)
                        .build();

                conjRepo.save(alert);
                saved++;

                // Immediately notify on HIGH/CRITICAL
                if (risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL) {
                    notificationService.broadcastConjunctionAlert(alert);
                }
            }
        }

        log.info("Conjunction screening complete: {} alerts saved", saved);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Coarse screen: if no checkpoint has distance < SCREEN_KM, skip fine scan.
     */
    private boolean coarseScreen(TleRecord a, TleRecord b, Instant from, Instant to) {
        for (Instant t = from; !t.isAfter(to); t = t.plus(1, ChronoUnit.HOURS)) {
            try {
                if (propagator.distanceBetween(a, b, t) < SCREEN_KM) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private record ConjunctionCandidate(Instant tca, double distanceKm, double relSpeedKms) {}

    /**
     * Fine scan every FINE_SEC seconds, track minimum distance.
     */
    private ConjunctionCandidate fineScan(TleRecord a, TleRecord b, Instant from, Instant to) {
        double  minDist   = Double.MAX_VALUE;
        Instant minTime   = null;
        double  minRelSpd = 0;

        for (Instant t = from; !t.isAfter(to); t = t.plusSeconds(FINE_SEC)) {
            try {
                double dist = propagator.distanceBetween(a, b, t);
                if (dist < minDist) {
                    minDist   = dist;
                    minTime   = t;
                    minRelSpd = propagator.relativeVelocity(a, b, t);
                }
            } catch (Exception ignored) { }
        }

        return minTime != null ? new ConjunctionCandidate(minTime, minDist, minRelSpd) : null;
    }

    private ConjunctionSummary toSummary(ConjunctionAlert c) {
        return ConjunctionSummary.builder()
                .id(c.getId())
                .noradIdA(c.getNoradIdA())
                .satelliteNameA(c.getSatelliteNameA())
                .noradIdB(c.getNoradIdB())
                .satelliteNameB(c.getSatelliteNameB())
                .tca(c.getTca())
                .missDistanceKm(c.getMissDistanceKm())
                .relativeSpeedKms(c.getRelativeSpeedKms())
                .riskLevel(c.getRiskLevel())
                .computedAt(c.getComputedAt())
                .build();
    }
}