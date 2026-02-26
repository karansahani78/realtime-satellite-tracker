package com.sattrack.controller;

import com.sattrack.dto.SatelliteDto;
import com.sattrack.service.OrbitService;
import com.sattrack.service.SatelliteService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST API for satellite tracking.
 *
 * All GET endpoints for satellite data are public (no auth required).
 * This is intentional: position data is freely available and maximizes
 * utility for casual users and developers building on top of the API.
 *
 * Rate limiting applied per client IP (configured in application.yml).
 */
@RestController
@RequestMapping("/api/satellites")
@RequiredArgsConstructor
@Validated
public class SatelliteController {

    private final SatelliteService satelliteService;
    private final OrbitService orbitService;

    /**
     * List all active satellites with optional category filter.
     * GET /api/satellites?page=0&size=20&category=ISS
     */
    @GetMapping
    @RateLimiter(name = "default")
    public ResponseEntity<Page<SatelliteDto.SatelliteSummary>> listSatellites(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(satelliteService.listSatellites(page, size, category));
    }

    /**
     * Search satellites by name, NORAD ID, or category.
     * GET /api/satellites/search?q=ISS&page=0
     */
    @GetMapping("/search")
    @RateLimiter(name = "default")
    public ResponseEntity<Page<SatelliteDto.SatelliteSummary>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(satelliteService.search(q, page, size));
    }

    /**
     * Get available satellite categories.
     * GET /api/satellites/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(satelliteService.getCategories());
    }

    /**
     * Get satellite metadata.
     * GET /api/satellites/{noradId}
     */
    @GetMapping("/{noradId}")
    public ResponseEntity<SatelliteDto.SatelliteSummary> getSatellite(
            @PathVariable String noradId) {
        return ResponseEntity.ok(satelliteService.getSatelliteByNoradId(noradId));
    }

    /**
     * Get current real-time position.
     * GET /api/satellites/{noradId}/current?lat=51.5&lon=-0.1
     */
    @GetMapping("/{noradId}/current")
    @RateLimiter(name = "position")
    public ResponseEntity<SatelliteDto.SatellitePosition> getCurrentPosition(
            @PathVariable String noradId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return ResponseEntity.ok(orbitService.getCurrentPosition(noradId, lat, lon));
    }

    /**
     * Predict position N minutes in the future.
     * GET /api/satellites/{noradId}/predict?minutes=10&lat=51.5&lon=-0.1
     */
    @GetMapping("/{noradId}/predict")
    @RateLimiter(name = "default")
    public ResponseEntity<SatelliteDto.PredictionResponse> predict(
            @PathVariable String noradId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10080) int minutes,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return ResponseEntity.ok(orbitService.predictAhead(noradId, minutes, lat, lon));
    }

    /**
     * Get orbital track between two timestamps.
     * GET /api/satellites/{noradId}/track?start=...&end=...&interval=60
     */
    @GetMapping("/{noradId}/track")
    @RateLimiter(name = "track")
    public ResponseEntity<SatelliteDto.TrackResponse> getTrack(
            @PathVariable String noradId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "60") @Min(10) @Max(3600) int interval) {
        return ResponseEntity.ok(orbitService.computeTrack(noradId, start, end, interval));
    }

    /**
     * Get the raw TLE data for a satellite.
     * GET /api/satellites/{noradId}/tle
     */
    @GetMapping("/{noradId}/tle")
    public ResponseEntity<SatelliteDto.TleInfo> getTle(@PathVariable String noradId) {
        return ResponseEntity.ok(satelliteService.getLatestTle(noradId));
    }
}
