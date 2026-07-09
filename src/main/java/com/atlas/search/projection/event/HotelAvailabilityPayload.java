package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Resource-facing hotel availability event (inventory-events.yaml HotelAvailabilityPayload, ADR-0008).
 * Copied from inventory-service. Search sets each night's {@code reserved} to the <b>absolute</b>
 * value, applied only if {@code version ≥} the stored version (last-writer-wins). Keyed by
 * {@code roomTypeId}.
 */
public record HotelAvailabilityPayload(
        @NotNull
        UUID reservationId,
        UUID bookingId,

        @NotNull
        UUID roomTypeId,
        UUID hotelId,

        @NotEmpty
        List<NightAvailability> nights,

        long version
) {}
