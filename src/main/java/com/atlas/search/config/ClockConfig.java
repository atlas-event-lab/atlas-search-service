package com.atlas.search.config;

import com.atlas.search.search.scheduler.TripOfferSweepProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a {@link Clock} bean so time-sensitive logic (TTL sweep, expiresAt)
 * is deterministic and testable (coding-standards §Unit Tests).
 */
@Configuration
@EnableConfigurationProperties(TripOfferSweepProperties.class)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
