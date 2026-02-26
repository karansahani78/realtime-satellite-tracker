package com.sattrack.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cache and HTTP client configuration.
 *
 * Cache TTL rationale:
 * - currentPosition (10s): Satellites move ~7 km/s; 10s ≈ 70 km position error max
 * - predictedPosition (60s): Future positions change more slowly relative to cached time
 * - trackData (300s): Multi-point tracks are CPU-intensive; 5min staleness acceptable
 * - satellites (600s): Satellite metadata rarely changes
 * - satelliteSearch (120s): Search results stable over short periods
 *
 * All caches are bounded to prevent memory exhaustion under load.
 */
@Configuration
public class AppConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Named caches with different TTLs
        manager.registerCustomCache("currentPosition",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.SECONDS)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        manager.registerCustomCache("predictedPosition",
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .build());

        manager.registerCustomCache("trackData",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(100)
                        .build());

        manager.registerCustomCache("satellites",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build());

        manager.registerCustomCache("satelliteSearch",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());

        return manager;
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .defaultHeader("User-Agent", "SatTrack-Platform/1.0 (github.com/sattrack)")
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}
