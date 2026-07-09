package com.atlas.search.projection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-night hotel availability projection (read_model.md; ADR-0009). One row per
 * {@code (resourceId = roomTypeId, stayDate)}. {@code capacity} is materialized from hotel catalog
 * events over the booking horizon; {@code reserved} is the <b>absolute</b> value from inventory hotel
 * events, applied last-writer-wins guarded by {@code version}. {@code available = capacity − reserved}.
 * Replaces the hotel side of the former shared {@code AvailabilityProjection}.
 */
@Entity
@Table(name = "room_type_availability")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RoomTypeNightAvailabilityProjection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The room type id (source UUID from Hotel Service); the Inventory rekey / partition key. */
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "stay_date", nullable = false, updatable = false)
    private LocalDate stayDate;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AvailabilityStatus status = AvailabilityStatus.ACTIVE;

    /** Monotonic guard for absolute {@code reserved} updates; apply an incoming update only if its version ≥ this. */
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RoomTypeNightAvailabilityProjection(UUID id, UUID resourceId, LocalDate stayDate,
                                               int capacity, int reserved, AvailabilityStatus status, long version) {
        this.id = id;
        this.resourceId = resourceId;
        this.stayDate = stayDate;
        this.capacity = capacity;
        this.reserved = reserved;
        this.status = status;
        this.version = version;
    }

    /** Derived — never stored, always computed on read. */
    public int getAvailable() {
        return Math.max(0, capacity - reserved);
    }
}
