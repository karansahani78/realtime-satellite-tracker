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
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for simple deployment; upgrade to /rabbitmq for scale
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Restrict in production
                .withSockJS();  // SockJS fallback for browsers without native WS
    }
}
