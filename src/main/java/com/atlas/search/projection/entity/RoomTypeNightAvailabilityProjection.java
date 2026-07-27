package com.atlas.search.projection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Per-night hotel availability projection (read_model.md; ADR-0009). One row per
 * {@code (resourceId = roomTypeId, stayDate)}. {@code capacity} is materialized from hotel catalog
 * events over the booking horizon; {@code reserved} is the <b>absolute</b> value from inventory hotel
 * events, applied last-writer-wins guarded by {@code version}. {@code available = capacity − reserved}.
 * Replaces the hotel side of the former shared {@code AvailabilityProjection}.
 *
 * <p><b>Why this implements {@link Persistable}.</b> The {@code @Id} is assigned by the
 * application (a random UUID), and {@code version} is a domain guard — <i>not</i> a JPA
 * {@code @Version}. Without those two, Spring Data's {@code isNew()} falls back to "is the id
 * null?", answers <i>no</i> for every row we create, and routes {@code save()} to
 * {@code EntityManager.merge()} instead of {@code persist()}. {@code merge()} on a detached
 * entity issues a <b>SELECT before every INSERT</b> to find out whether the row already exists.
 *
 * <p>That is invisible for one row and ruinous for the hotel calendar, which materializes one
 * row per room type per night over the booking horizon: a single {@code hotel.created} writes
 * roughly a thousand rows, so the redundant SELECTs double an already large amount of database
 * round-trips. Declaring newness explicitly keeps those inserts as inserts, and lets Hibernate
 * batch them (see {@code hibernate.jdbc.batch_size} in application.yml). Origin: Experiment 07.
 */
@Entity
@Table(name = "room_type_availability")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RoomTypeNightAvailabilityProjection implements Persistable<UUID> {

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

    /**
     * Transient newness flag backing {@link #isNew()}. True only between construction and the
     * first flush; anything loaded from the database is, by definition, not new.
     */
    @Transient
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private boolean newRow;

    public RoomTypeNightAvailabilityProjection(
            UUID id,
            UUID resourceId,
            LocalDate stayDate,
            int capacity,
            int reserved,
            AvailabilityStatus status,
            long version) {
        this.id = id;
        this.resourceId = resourceId;
        this.stayDate = stayDate;
        this.capacity = capacity;
        this.reserved = reserved;
        this.status = status;
        this.version = version;
        this.newRow = true; // constructed by us => an INSERT, never a merge
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }

    /**
     * Once the row exists — loaded from the database, or just inserted — it is no longer new, so
     * a later {@code save()} on the same instance updates instead of attempting another insert.
     */
    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newRow = false;
    }

    /** Derived — never stored, always computed on read. */
    public int getAvailable() {
        return Math.max(0, capacity - reserved);
    }
}
