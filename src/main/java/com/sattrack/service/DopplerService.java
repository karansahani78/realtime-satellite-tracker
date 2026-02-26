package com.sattrack.service;

import com.sattrack.dto.TrackingDto.DopplerRequest;
import com.sattrack.dto.TrackingDto.DopplerResult;
import com.sattrack.exception.SatelliteNotFoundException;
import com.sattrack.repository.TleRepository;
import com.sattrack.service.OrekitPropagator.LookAngles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Calculates the Doppler-shifted frequency for a satellite pass.
 *
 * Formula:
 *   f_observed = f_nominal × (c / (c + v_radial))
 *
 * where:
 *   c         = speed of light (299,792.458 km/s)
 *   v_radial  = range rate in km/s (positive = satellite moving away)
 *
 * Sign convention: rangeRate > 0 means receding → frequency drops.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DopplerService {

    private static final double C_KMS = 299_792.458; // speed of light in km/s

    private final TleRepository    tleRepository;
    private final OrekitPropagator propagator;

    // ─────────────────────────────────────────────────────────────────────────
    // Current Doppler (real-time)
    // ─────────────────────────────────────────────────────────────────────────

    public DopplerResult currentDoppler(DopplerRequest req) {
        return computeAt(req, Instant.now());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Doppler curve over a pass (for plotting)
    // Returns one data point every 5 seconds for up to 20 minutes
    // ─────────────────────────────────────────────────────────────────────────

    public java.util.List<DopplerResult> dopplerCurve(DopplerRequest req,
                                                      Instant passStart,
                                                      Instant passEnd) {
        var results = new java.util.ArrayList<DopplerResult>();
        Instant cursor = passStart;
        while (!cursor.isAfter(passEnd)) {
            try {
                results.add(computeAt(req, cursor));
            } catch (Exception ignored) { }
            cursor = cursor.plusSeconds(5);
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core
    // ─────────────────────────────────────────────────────────────────────────

    private DopplerResult computeAt(DopplerRequest req, Instant at) {
        var tle = tleRepository.findLatestByNoradId(req.getNoradId())
                .orElseThrow(() -> new SatelliteNotFoundException(req.getNoradId()));

        LookAngles angles = propagator.lookAngles(
                tle, at,
                req.getObserverLat(),
                req.getObserverLon(),
                req.getObserverAltMeters());

        // rangeRate: positive = moving away (receding), negative = approaching
        double vRadial = angles.rangeRateKms();

        // Doppler formula — non-relativistic (good enough for LEO, v << c)
        double fNom      = req.getFrequencyMhz();
        double shiftHz   = -(fNom * 1e6) * vRadial / C_KMS;   // Hz
        double fObs      = fNom + shiftHz / 1e6;               // MHz

        return DopplerResult.builder()
                .noradId(req.getNoradId())
                .nominalFrequencyMhz(fNom)
                .dopplerShiftHz(shiftHz)
                .observedFrequencyMhz(fObs)
                .radialVelocityKms(vRadial)
                .elevationDeg(angles.elevationDeg())
                .rangKm(angles.rangeKm())
                .computedAt(at)
                .build();
    }
}