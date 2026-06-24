package com.atlas.search.search.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigurationProperties(prefix = "atlas.search.trip-offer")
public class TripOfferSweepProperties {

    @DurationUnit(ChronoUnit.MINUTES)
    private Duration ttl = Duration.ofMinutes(15);

    private long sweepIntervalMs = 300_000L;

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public long getSweepIntervalMs() { return sweepIntervalMs; }
    public void setSweepIntervalMs(long sweepIntervalMs) { this.sweepIntervalMs = sweepIntervalMs; }
}
