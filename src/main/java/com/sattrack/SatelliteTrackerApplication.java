package com.sattrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Satellite Tracking Platform.
 *
 * Architecture decisions:
 * - @EnableCaching: Caffeine in-memory cache for high-frequency position reads
 * - @EnableScheduling: Background TLE refresh every 6 hours to keep data fresh
 * - Layered architecture: Controller → Service → Repository with DTOs at each boundary
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
public class SatelliteTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SatelliteTrackerApplication.class, args);
    }
}
