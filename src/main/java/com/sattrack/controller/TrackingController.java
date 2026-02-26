package com.sattrack.controller;

import com.sattrack.dto.TrackingDto.*;
import com.sattrack.entity.User;
import com.sattrack.repository.UserRepository;
import com.sattrack.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrackingController {

    private final PassPredictionService passPredictionService;
    private final DopplerService dopplerService;
    private final ConjunctionService conjunctionService;
    private final NotificationService notificationService;
    private final UserAlertService userAlertService;
    private final UserRepository userRepository;   // 🔥 REQUIRED FIX

    // ═════════════════════════════════════════════════════════════════════════
    // PASS PREDICTIONS
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/passes/predict")
    public ResponseEntity<List<PassSummary>> predictPasses(
            @RequestBody PassRequest req,
            @AuthenticationPrincipal(errorOnInvalidType = false) Object principal) {

        Long userId = extractUserId(principal);
        List<PassSummary> passes = passPredictionService.predictPasses(req, userId);
        return ResponseEntity.ok(passes);
    }

    @GetMapping("/passes/satellite/{noradId}")
    public ResponseEntity<List<PassSummary>> getPassesForSatellite(
            @PathVariable String noradId,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "0") double alt,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10.0") double minElevation,
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

        return ResponseEntity.ok(
                passPredictionService.predictPasses(req, extractUserId(principal)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DOPPLER
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/doppler/current")
    public ResponseEntity<DopplerResult> currentDoppler(@RequestBody DopplerRequest req) {
        return ResponseEntity.ok(dopplerService.currentDoppler(req));
    }

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

    @GetMapping("/conjunctions")
    public ResponseEntity<Page<ConjunctionSummary>> getConjunctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(conjunctionService.getUpcoming(page, size));
    }

    @GetMapping("/conjunctions/satellite/{noradId}")
    public ResponseEntity<List<ConjunctionSummary>> getConjunctionsForSatellite(
            @PathVariable String noradId) {
        return ResponseEntity.ok(conjunctionService.getBySatellite(noradId));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/notifications")
    public ResponseEntity<NotificationPage> getNotifications(
            @AuthenticationPrincipal Object principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                notificationService.getForUser(requireUserId(principal), page, size));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal Object principal) {

        long count = notificationService.unreadCount(requireUserId(principal));
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {

        notificationService.markRead(id, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal Object principal) {

        notificationService.markAllRead(requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ALERT PREFERENCES
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertPreferenceDto>> getAlerts(
            @AuthenticationPrincipal Object principal) {

        return ResponseEntity.ok(
                userAlertService.getForUser(requireUserId(principal)));
    }

    @PostMapping("/alerts")
    public ResponseEntity<AlertPreferenceDto> createAlert(
            @RequestBody AlertPreferenceRequest req,
            @AuthenticationPrincipal Object principal) {

        return ResponseEntity.ok(
                userAlertService.create(requireUserId(principal), req));
    }

    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {

        userAlertService.delete(id, requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 🔥 FIXED AUTH HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Long extractUserId(Object principal) {

        if (principal == null) return null;

        String username = null;

        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        }

        if (principal instanceof String str) {
            username = str;
        }

        if (username == null) return null;

        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private Long requireUserId(Object principal) {
        Long id = extractUserId(principal);
        if (id == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return id;
    }
}