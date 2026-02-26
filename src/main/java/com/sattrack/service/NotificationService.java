package com.sattrack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sattrack.dto.TrackingDto.NotificationDto;
import com.sattrack.dto.TrackingDto.NotificationPage;
import com.sattrack.entity.ConjunctionAlert;
import com.sattrack.entity.Notification;
import com.sattrack.entity.Notification.Channel;
import com.sattrack.entity.Notification.NotificationType;
import com.sattrack.entity.PassPrediction;
import com.sattrack.entity.UserAlert;
import com.sattrack.repository.NotificationRepository;
import com.sattrack.repository.UserAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    private final NotificationRepository notifRepo;
    private final UserAlertRepository userAlertRepo;
    private final EmailService emailService;
    private final SimpMessagingTemplate  wsTemplate;   // WebSocket push
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Read
    // ─────────────────────────────────────────────────────────────────────────

    public NotificationPage getForUser(Long userId, int page, int size) {
        Page<Notification> pg = notifRepo.findByUserIdOrderBySentAtDesc(
                userId, PageRequest.of(page, size, Sort.by("sentAt").descending()));

        return NotificationPage.builder()
                .notifications(pg.map(this::toDto).toList())
                .unreadCount(notifRepo.countByUserIdAndReadFalse(userId))
                .totalPages(pg.getTotalPages())
                .totalElements(pg.getTotalElements())
                .build();
    }

    public long unreadCount(Long userId) {
        return notifRepo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long notifId, Long userId) {
        notifRepo.markReadById(notifId, userId, Instant.now());
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifRepo.markAllReadForUser(userId, Instant.now());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send — Pass upcoming alert
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void sendPassAlert(UserAlert alert, PassPrediction pass) {
        String aosStr = FMT.format(pass.getAos());
        String title  = "🛰 Pass Alert: " + pass.getSatelliteName();
        String msg    = String.format(
                "%s will be visible at %s — max elevation %.1f°, duration %d min %02d sec. " +
                        "Direction: %s → %s",
                pass.getSatelliteName(),
                aosStr,
                pass.getMaxElevation(),
                pass.getDurationSeconds() / 60,
                pass.getDurationSeconds() % 60,
                azToCompass(pass.getAosAzimuth()),
                azToCompass(pass.getLosAzimuth()));

        String payload = toJson(Map.of(
                "type",    "PASS",
                "noradId", pass.getNoradId(),
                "passId",  pass.getId(),
                "aos",     pass.getAos().toString()));

        Notification notif = persist(alert.getUserId(), NotificationType.PASS_UPCOMING,
                title, msg, payload, Channel.IN_APP);

        // Push over WebSocket
        pushToUser(alert.getUserId(), notif);

        // Email if user has email alerts configured
        emailService.sendPassAlert(alert.getUserId(), pass, notif);

        // Update last triggered
        alert.setLastTriggeredAt(Instant.now());
        userAlertRepo.save(alert);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send — Conjunction alert (broadcast to all subscribed users)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void broadcastConjunctionAlert(ConjunctionAlert conj) {
        String risk  = conj.getRiskLevel().name();
        String title = String.format("⚠ %s Conjunction Alert", risk);
        String msg   = String.format(
                "%s and %s will pass within %.2f km at %s (relative speed %.2f km/s)",
                conj.getSatelliteNameA(),
                conj.getSatelliteNameB(),
                conj.getMissDistanceKm(),
                FMT.format(conj.getTca()),
                conj.getRelativeSpeedKms());

        String payload = toJson(Map.of(
                "type",       "CONJUNCTION",
                "conjId",     conj.getId(),
                "noradIdA",   conj.getNoradIdA(),
                "noradIdB",   conj.getNoradIdB(),
                "distanceKm", conj.getMissDistanceKm(),
                "tca",        conj.getTca().toString()));

        // Find all users with CONJUNCTION alert type active
        NotificationType type = conj.getRiskLevel() == ConjunctionAlert.RiskLevel.CRITICAL
                ? NotificationType.CONJUNCTION_CRITICAL
                : NotificationType.CONJUNCTION_WARNING;

        userAlertRepo.findAllActiveByType(UserAlert.AlertType.CONJUNCTION)
                .forEach(ua -> {
                    Notification n = persist(ua.getUserId(), type, title, msg, payload, Channel.IN_APP);
                    pushToUser(ua.getUserId(), n);
                    emailService.sendConjunctionAlert(ua.getUserId(), conj, n);
                });

        // Also push to public WebSocket topic for dashboard display
        wsTemplate.convertAndSend("/topic/conjunctions", payload);

        conj.setNotificationSent(true);
        log.info("Conjunction alert broadcast: {} - {} @ {:.2f}km",
                conj.getNoradIdA(), conj.getNoradIdB(), conj.getMissDistanceKm());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private Notification persist(Long userId, NotificationType type,
                                 String title, String message,
                                 String payload, Channel channel) {
        return notifRepo.save(Notification.builder()
                .userId(userId)
                .notificationType(type)
                .title(title)
                .message(message)
                .payload(payload)
                .sentAt(Instant.now())
                .read(false)
                .channel(channel)
                .build());
    }

    private void pushToUser(Long userId, Notification n) {
        try {
            wsTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    toDto(n));
        } catch (Exception ex) {
            log.warn("WebSocket push failed for user {}: {}", userId, ex.getMessage());
        }
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .notificationType(n.getNotificationType())
                .title(n.getTitle())
                .message(n.getMessage())
                .payload(n.getPayload())
                .sentAt(n.getSentAt())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .build();
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private String azToCompass(double az) {
        String[] d = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return d[(int) Math.round(az / 22.5) % 16];
    }
}