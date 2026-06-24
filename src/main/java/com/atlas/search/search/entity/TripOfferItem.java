package com.atlas.search.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A bookable line item inside a {@link TripOffer} snapshot.
 * Frozen at search time; never updated after creation.
 */
@Entity
@Table(name = "trip_offer_items")
@Getter
@Setter
@NoArgsConstructor
public class TripOfferItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_offer_id", nullable = false, updatable = false)
    private TripOffer tripOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10, updatable = false)
    private TripItemType type;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false, length = 3, updatable = false)
    private String unitPriceCurrency;

    @Column(name = "line_total_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal lineTotalAmount;

    @Column(name = "line_total_currency", nullable = false, length = 3, updatable = false)
    private String lineTotalCurrency;

    // ── Flight denormalized display ───────────────────────────────────────────

    @Column(name = "airline")
    private String airline;

    @Column(name = "departure_time")
    private Instant departureTime;

    @Column(name = "arrival_time")
    private Instant arrivalTime;

    @Column(name = "stops")
    private Integer stops;

    // ── Hotel denormalized display ────────────────────────────────────────────

    @Column(name = "hotel_name")
    private String hotelName;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "nights")
    private Integer nights;
}
