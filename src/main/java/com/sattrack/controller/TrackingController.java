package com.sattrack.controller;

import com.sattrack.dto.TrackingDto.*;
import com.sattrack.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * All new tracking feature endpoints.
 *
 * Base path: /api/v1
 *
 * Pass Predictions:
 *   POST /api/v1/passes/predict
 *   GET  /api/v1/passes/satellite/{noradId}
 *   GET  /api/v1/passes/upcoming         (authenticated — user's watched sats)
 *
 * Doppler:
 *   POST /api/v1/doppler/current
 *   POST /api/v1/doppler/curve
 *
 * Conjunctions:
 *   GET  /api/v1/conjunctions
 *   GET  /api/v1/conjunctions/satellite/{noradId}
 *
 * Notifications:
 *   GET  /api/v1/notifications
 *   GET  /api/v1/notifications/unread-count
 *   POST /api/v1/notifications/{id}/read
 *   POST /api/v1/notifications/read-all
 *
 * Alert Preferences:
 *   GET    /api/v1/alerts
 *   POST   /api/v1/alerts
 *   DELETE /api/v1/alerts/{id}
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrackingController {

    private final PassPredictionService passPredictionService;
    private final DopplerService        dopplerService;
    private final ConjunctionService    conjunctionService;
    private final NotificationService   notificationService;
    private final UserAlertService      userAlertService;

    // ═════════════════════════════════════════════════════════════════════════
    // PASS PREDICTIONS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Compute pass predictions for any observer location.
     * No auth required — anyone can query passes for any location.
     *
     * POST /api/v1/passes/predict
     * Body: { noradId, observerLat, observerLon, observerAltMeters, days, minElevation, visibleOnly }
     */
    @PostMapping("/passes/predict")
    public ResponseEntity<List<PassSummary>> predictPasses(
            @RequestBody PassRequest req,
            @AuthenticationPrincipal(errorOnInvalidType = false) Object principal) {

        Long userId = extractUserId(principal);
        List<PassSummary> passes = passPredictionService.predictPasses(req, userId);
        return ResponseEntity.ok(passes);
    }

    /**
     * GET /api/v1/passes/satellite/{noradId}?lat=&lon=&days=&minElevation=&visibleOnly=
     */
    @GetMapping("/passes/satellite/{noradId}")
    public ResponseEntity<List<PassSummary>> getPassesForSatellite(
            @PathVariable String noradId,
            @RequestParam double  lat,
            @RequestParam double  lon,
            @RequestParam(defaultValue = "0")    double  alt,
            @RequestParam(defaultValue = "7")    int     days,
            @RequestParam(defaultValue = "10.0") double  minElevation,
            @RequestParam(defaultValue = "false") boolean visibleOnly,
            @AuthenticationPrincipal(errorOnInvalidType = false) Object principal) {

        PassRequest req = PassRequest.builder()
                .noradId(noradId)
                .observerLat(lat)
                .observerLon(lon)
                .observerAltMeters(alt)
                .days(days)
                .minElevation(minElevation)
                .visibleOnly(visibleOnly)
                .build();

        return ResponseEntity.ok(passPredictionService.predictPasses(req, extractUserId(principal)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DOPPLER
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Current Doppler shift for a satellite at the observer's position.
     * POST /api/v1/doppler/current
     */
    @PostMapping("/doppler/current")
    public ResponseEntity<DopplerResult> currentDoppler(@RequestBody DopplerRequest req) {
        return ResponseEntity.ok(dopplerService.currentDoppler(req));
    }

    /**
     * Doppler curve over a pass window (for graphing).
     * POST /api/v1/doppler/curve?passStart=&passEnd=
     */
    @PostMapping("/doppler/curve")
    public ResponseEntity<List<DopplerResult>> dopplerCurve(
            @RequestBody DopplerRequest req,
            @RequestParam Instant passStart,
            @RequestParam Instant passEnd) {
        return ResponseEntity.ok(dopplerService.dopplerCurve(req, passStart, passEnd));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONJUNCTIONS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/v1/conjunctions?page=&size=
     */
    @GetMapping("/conjunctions")
    public ResponseEntity<Page<ConjunctionSummary>> getConjunctions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(conjunctionService.getUpcoming(page, size));
    }

    /**
     * GET /api/v1/conjunctions/satellite/{noradId}
     */
    @GetMapping("/conjunctions/satellite/{noradId}")
    public ResponseEntity<List<ConjunctionSummary>> getConjunctionsForSatellite(
            @PathVariable String noradId) {
        return ResponseEntity.ok(conjunctionService.getBySatellite(noradId));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═════════════════════════════════════════════════════════════════════════

    /** GET /api/v1/notifications?page=&size= */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationPage> getNotifications(
            @AuthenticationPrincipal Object principal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                notificationService.getForUser(requireUserId(principal), page, size));
    }

    /** GET /api/v1/notifications/unread-count */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal Object principal) {
        long count = notificationService.unreadCount(requireUserId(principal));
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** POST /api/v1/notifications/{id}/read */
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        notificationService.markRead(id, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/notifications/read-all */
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal Object principal) {
        notificationService.markAllRead(requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // USER ALERT PREFERENCES
    // ═════════════════════════════════════════════════════════════════════════

    /** GET /api/v1/alerts */
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertPreferenceDto>> getAlerts(
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(userAlertService.getForUser(requireUserId(principal)));
    }

    /** POST /api/v1/alerts */
    @PostMapping("/alerts")
    public ResponseEntity<AlertPreferenceDto> createAlert(
            @RequestBody AlertPreferenceRequest req,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(
                userAlertService.create(requireUserId(principal), req));
    }

    /** DELETE /api/v1/alerts/{id} */
    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        userAlertService.delete(id, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Extract userId without throwing — for anonymous-friendly endpoints */
    private Long extractUserId(Object principal) {
        if (principal instanceof com.sattrack.entity.User u) return u.getId();
        return null;
    }

    /** Extract userId, throw 401 if not authenticated */
    private Long requireUserId(Object principal) {
        Long id = extractUserId(principal);
        if (id == null) throw new org.springframework.security.access.AccessDeniedException(
                "Authentication required");
        return id;
    }
}