package com.sattrack.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelOption;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cache and HTTP client configuration.
 *
 * Cache TTL rationale:
 * - currentPosition (10s): Satellites move ~7 km/s; 10s ≈ 70 km error max
 * - predictedPosition (60s): Future positions change slower
 * - trackData (300s): CPU-heavy, short staleness acceptable
 * - satellites (600s): Metadata rarely changes
 * - satelliteSearch (120s): Stable search results
 */
@Configuration
public class AppConfig {

    /* ===================== CACHE CONFIG ===================== */

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

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
        // ADD BELOW existing caches
        manager.registerCustomCache("tleFetchCooldown",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES) // ⏳ BACKOFF
                        .maximumSize(50_000)
                        .build());

        return manager;
    }

    /* ===================== WEBCLIENT (FIXED) ===================== */

    @Bean
    public WebClient webClient() {

        HttpClient httpClient = HttpClient.create()
                // ✅ FORCE IPV4 DNS (FIXES N2YO + CELESTRAK)
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "SatTrack-Platform/1.0 (github.com/sattrack)")
                .codecs(codecs ->
                        codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}