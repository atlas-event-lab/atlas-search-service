package com.atlas.search.search.dto;

import java.time.Instant;
import java.util.UUID;

/** A bookable line item within a TripDetail (search.yaml — TripItem). */
public record TripItemDto(
        String type,
        UUID resourceId,
        int quantity,
        MoneyDto unitPrice,
        MoneyDto lineTotal,
        // FLIGHT display fields
        String airline,
        Instant departureTime,
        Instant arrivalTime,
        Integer stops,
        // HOTEL display fields
        String hotelName,
        Integer rating,
        Integer nights
) {}
