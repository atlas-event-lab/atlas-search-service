package com.atlas.search.search.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TripOffer snapshot materialized at search time from the index projections.
 * Identified by {@code id} (tripId). Valid while {@code expiresAt > now()}.
 * Not event-sourced and exempt from projection rebuild (read_model.md).
 */
@Entity
@Table(name = "trip_offers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TripOffer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Groups all offers produced in one search call. */
    @Column(name = "search_id", nullable = false, updatable = false)
    private UUID searchId;

    /** JSON snapshot of the originating search criteria for reproducibility. */
    @Column(name = "search_criteria", nullable = false, updatable = false, columnDefinition = "text")
    private String searchCriteria;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "flight_count", nullable = false, updatable = false)
    private int flightCount;

    @Column(name = "hotel_count", nullable = false, updatable = false)
    private int hotelCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "tripOffer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TripOfferItem> items = new ArrayList<>();

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now) || expiresAt.equals(now);
    }
}
