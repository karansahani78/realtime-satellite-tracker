package com.sattrack.service;

import com.sattrack.dto.SatelliteDto;
import com.sattrack.entity.TleRecord;
import com.sattrack.exception.TleNotFoundException;
import com.sattrack.repository.TleRepository;
import com.sattrack.util.CoordinateConverter;
import com.sattrack.util.Sgp4Propagator;
import com.sattrack.util.TleElements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrbitService {

    private final Sgp4Propagator propagator;
    private final TleRepository tleRepository;
    private final TleFetcherService tleFetcherService;
    private final CacheManager cacheManager;

    private static final double TWO_PI = 2.0 * Math.PI;

    // Only one fetch per NORAD ID in-flight at a time
    private final ConcurrentHashMap<String, CompletableFuture<Void>> inFlightFetches =
            new ConcurrentHashMap<>();

    @Cacheable(value = "currentPosition", key = "#noradId + ':' + T(java.time.Instant).now().truncatedTo(T(java.time.temporal.ChronoUnit).SECONDS).epochSecond / 10")
    public SatelliteDto.SatellitePosition getCurrentPosition(
            String noradId,
            Double observerLat,
            Double observerLon) {

        return getPositionAt(noradId, Instant.now(), observerLat, observerLon);
    }

    @Cacheable(value = "predictedPosition", key = "#noradId + ':' + #targetTime.epochSecond")
    public SatelliteDto.SatellitePosition getPositionAt(
            String noradId,
            Instant targetTime,
            Double observerLat,
            Double observerLon) {

        TleRecord tle = getLatestTle(noradId);
        TleElements elements = TleElements.parse(tle.getLine0(), tle.getLine1(), tle.getLine2());

        Sgp4Propagator.EciState eciState = propagator.propagate(elements, targetTime);
        CoordinateConverter.GeodeticCoords geo = CoordinateConverter.eciToGeodetic(eciState, targetTime);

        double periodMinutes = TWO_PI / elements.meanMotionRadPerMin();

        SatelliteDto.LookAnglesDto lookAngles = null;
        if (observerLat != null && observerLon != null) {
            CoordinateConverter.LookAngles angles = CoordinateConverter.computeLookAngles(
                    geo, observerLat, observerLon, 0.0);
            lookAngles = SatelliteDto.LookAnglesDto.builder()
                    .azimuthDeg(round(angles.azimuthDeg(), 2))
                    .elevationDeg(round(angles.elevationDeg(), 2))
                    .rangeKm(round(angles.rangeKm(), 1))
                    .visible(angles.elevationDeg() > 0)
                    .build();
        }

        return SatelliteDto.SatellitePosition.builder()
                .noradId(noradId)
                .name(elements.name())
                .timestamp(targetTime)
                .latitudeDeg(round(geo.latitudeDeg(), 6))
                .longitudeDeg(round(geo.longitudeDeg(), 6))
                .altitudeKm(round(geo.altitudeKm(), 3))
                .speedKmPerS(round(geo.speedKmPerS(), 3))
                .velocityKmPerS(round(eciState.speed(), 3))
                .orbitalPeriodMinutes(round(periodMinutes, 2))
                .lookAngles(lookAngles)
                .build();
    }

    @Cacheable(value = "trackData",
            key = "#noradId + ':' + #start.epochSecond + ':' + #end.epochSecond + ':' + #intervalSecs")
    public SatelliteDto.TrackResponse computeTrack(
            String noradId, Instant start, Instant end, int intervalSecs) {

        if (intervalSecs < 1) intervalSecs = 30;
        if (intervalSecs > 3600) intervalSecs = 3600;

        long durationSecs = end.getEpochSecond() - start.getEpochSecond();
        if (durationSecs <= 0) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        int maxPoints = 1440;
        long actualInterval = Math.max(intervalSecs, durationSecs / maxPoints);

        TleRecord tle = getLatestTle(noradId);
        TleElements elements = TleElements.parse(tle.getLine0(), tle.getLine1(), tle.getLine2());

        List<SatelliteDto.TrackPoint> points = new ArrayList<>();
        Instant current = start;

        while (!current.isAfter(end)) {
            Sgp4Propagator.EciState eciState = propagator.propagate(elements, current);
            CoordinateConverter.GeodeticCoords geo = CoordinateConverter.eciToGeodetic(eciState, current);

            points.add(SatelliteDto.TrackPoint.builder()
                    .timestamp(current)
                    .latitudeDeg(round(geo.latitudeDeg(), 6))
                    .longitudeDeg(round(geo.longitudeDeg(), 6))
                    .altitudeKm(round(geo.altitudeKm(), 3))
                    .speedKmPerS(round(geo.speedKmPerS(), 3))
                    .build());

            current = current.plusSeconds(actualInterval);
        }

        return SatelliteDto.TrackResponse.builder()
                .noradId(noradId)
                .name(elements.name())
                .startTime(start)
                .endTime(end)
                .intervalSeconds((int) actualInterval)
                .points(points)
                .build();
    }

    public SatelliteDto.PredictionResponse predictAhead(
            String noradId, int minutes, Double obsLat, Double obsLon) {

        Instant target = Instant.now().plus(minutes, ChronoUnit.MINUTES);
        SatelliteDto.SatellitePosition pos = getPositionAt(noradId, target, obsLat, obsLon);

        return SatelliteDto.PredictionResponse.builder()
                .noradId(noradId)
                .name(pos.name())
                .predictedAt(Instant.now())
                .minutesAhead(minutes)
                .position(pos)
                .build();
    }

    private TleRecord getLatestTle(String noradId) {
        return tleRepository.findLatestByNoradId(noradId)
                .orElseGet(() -> {
                    log.info("No TLE found for NORAD {}; attempting on-demand fetch", noradId);
                    fetchWithDedup(noradId);
                    return tleRepository.findLatestByNoradId(noradId)
                            .orElseThrow(() -> new TleNotFoundException(
                                    "No TLE data available for satellite " + noradId));
                });
    }

    /**
     * Ensures only ONE fetch runs per NORAD ID at a time.
     * Other callers that arrive while a fetch is in-flight simply wait
     * on the existing future rather than starting a duplicate fetch.
     */
    private void fetchWithDedup(String noradId) {

        Cache cooldown = cacheManager.getCache("tleFetchCooldown");

        // 🚫 COOLDOWN CHECK
        if (cooldown != null && cooldown.get(noradId) != null) {
            log.debug("TLE fetch for {} is in cooldown, skipping", noradId);
            return;
        }

        CompletableFuture<Void> myFuture = new CompletableFuture<>();
        CompletableFuture<Void> existing = inFlightFetches.putIfAbsent(noradId, myFuture);

        if (existing != null) {
            log.debug("Fetch already in-flight for NORAD {}, waiting...", noradId);
            try {
                existing.get(35, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            return;
        }

        try {
            boolean success = tleFetcherService.fetchSingleSatellite(noradId);

            if (!success && cooldown != null) {
                cooldown.put(noradId, Boolean.TRUE); // ⛔ BACKOFF
            }

        } finally {
            inFlightFetches.remove(noradId);
            myFuture.complete(null);
        }
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}