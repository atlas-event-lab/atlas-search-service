package com.atlas.search.search.dto;

import java.time.Instant;
import java.util.UUID;

/** One bookable flight, live from the projections (search.yaml FlightOffer). */
public record FlightOffer(
        UUID flightId,
        String airline,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        int durationMinutes,
        int stops,
        MoneyDto basePrice,
        int available
) {}
