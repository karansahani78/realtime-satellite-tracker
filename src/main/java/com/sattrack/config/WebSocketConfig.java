package com.sattrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * WHY STOMP instead of raw WebSocket?
 * - STOMP provides a topic/subscription model (pub/sub)
 * - Clients subscribe to "/topic/satellites/{noradId}" and receive pushes
 * - No client-side polling needed; server pushes on a schedule
 * - Spring's @MessageMapping makes handler code clean and testable
 *
 * For production at scale, replace the in-memory broker with RabbitMQ or
 * Kafka STOMP adapter so multiple backend instances can share state.
 *
 * CHANGES FROM ORIGINAL:
 *  1. Added "/queue" to enableSimpleBroker — required for per-user
 *     queues used by NotificationService.convertAndSendToUser()
 *  2. Added setUserDestinationPrefix("/user") — Spring needs this to
 *     route convertAndSendToUser() to the correct subscriber.
 *     Without it, user-targeted messages are silently dropped.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/topic" = broadcast (one-to-many, e.g. conjunction alerts)
        // "/queue"  = per-user (one-to-one, e.g. personal pass notifications)
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages FROM client TO server (@MessageMapping methods)
        registry.setApplicationDestinationPrefixes("/app");

        // Required for convertAndSendToUser(userId, "/queue/notifications", ...)
        // to resolve to "/user/{userId}/queue/notifications"
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Restrict in production
                .withSockJS();  // SockJS fallback for browsers without native WS
    }
}