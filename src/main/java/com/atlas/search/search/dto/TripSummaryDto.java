package com.atlas.search.search.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One TripOffer snapshot in the search results page (search.yaml — TripSummary). */
public record TripSummaryDto(
        UUID tripId,
        String origin,
        String destination,
        LocalDate departureDate,
        LocalDate returnDate,
        String airline,
        int durationMinutes,
        int stops,
        int flights,
        int hotels,
        MoneyDto total,
        Instant expiresAt
) {}
