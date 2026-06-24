package com.atlas.search.search.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full TripOffer snapshot returned by GET /trips/{tripId} (search.yaml — TripDetail). */
public record TripDetailDto(
        UUID tripId,
        List<TripItemDto> items,
        MoneyDto total,
        Instant createdAt,
        Instant expiresAt
) {}
