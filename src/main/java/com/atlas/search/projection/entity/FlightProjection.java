package com.atlas.search.projection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Read model for flight catalog data (read_model.md — FlightProjection).
 * Built exclusively from FlightCreated/Updated/Deleted events. Never written by hand.
 */
@Entity
@Table(name = "flight_projections")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FlightProjection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "airline", nullable = false, length = 100)
    private String airline;

    @Column(name = "origin", nullable = false, length = 10)
    private String origin;

    @Column(name = "destination", nullable = false, length = 10)
    private String destination;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /**
     * Segment count − 1. Populated when Flight Service publishes FlightSegment data.
     * Until dep. A1 is resolved, this value defaults to 0 and the stops/nonStop filter is inert.
     */
    @Column(name = "stops", nullable = false)
    private int stops;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Total seats, from catalog events (ADR-0009 — flight availability is folded in here). */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /** Absolute reserved seats, from inventory flight events (last-writer-wins, guarded by {@link #version}). */
    @Column(name = "reserved", nullable = false)
    private int reserved;

    /** Monotonic guard for absolute {@code reserved} updates; apply an incoming update only if its version ≥ this. */
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectionStatus status = ProjectionStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Seats still available for reservation, never negative (ADR-0009). */
    public int getAvailable() {
        return Math.max(0, capacity - reserved);
    }
}
