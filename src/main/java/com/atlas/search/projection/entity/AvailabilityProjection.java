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
import java.util.UUID;

/**
 * Live availability per reservable resource (read_model.md — AvailabilityProjection).
 * {@code available = capacity − reserved}. Capacity is set by catalog events;
 * reserved is adjusted by Inventory resource-facing events.
 */
@Entity
@Table(name = "availability_projections")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityProjection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 10, updatable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "reserved", nullable = false)
    private Integer reserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AvailabilityStatus status = AvailabilityStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Derived value — never stored, always computed on read. */
    public int getAvailable() {
        return Math.max(0, capacity - reserved);
    }

    public enum AvailabilityStatus {
        ACTIVE, DISABLED
    }
}
