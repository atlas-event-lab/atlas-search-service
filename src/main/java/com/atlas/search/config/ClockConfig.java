package com.atlas.search.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Clock} bean so time-sensitive logic (validation, the hotel calendar's "today")
 * is deterministic and testable (coding-standards §Unit Tests), and binds
 * {@link HotelSearchProperties}.
 */
@Configuration
@EnableConfigurationProperties(HotelSearchProperties.class)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
