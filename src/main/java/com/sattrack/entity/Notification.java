package com.sattrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user",      columnList = "user_id, sent_at DESC"),
        @Index(name = "idx_notif_unread",    columnList = "user_id, read"),
        @Index(name = "idx_notif_type",      columnList = "notification_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notif_seq")
    @SequenceGenerator(name = "notif_seq", sequenceName = "notifications_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** JSON payload for deep-linking in the frontend (e.g. noradId, passId) */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read")
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    /** Channel this was delivered on */
    @Column(name = "channel", length = 10)
    @Enumerated(EnumType.STRING)
    private Channel channel;

    public enum NotificationType {
        PASS_UPCOMING,
        PASS_NOW,
        CONJUNCTION_WARNING,
        CONJUNCTION_CRITICAL,
        TLE_STALE,
        REENTRY_WARNING,
        SYSTEM
    }

    public enum Channel {
        IN_APP,
        EMAIL,
        PUSH
    }
}