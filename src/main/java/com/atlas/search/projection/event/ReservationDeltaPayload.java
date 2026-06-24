package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Resource-facing payload for a single reservation item (inventory-events.yaml ReservationDeltaPayload).
 * Copied from inventory-service. Search adjusts AvailabilityProjection from {@code resourceId} and
 * {@code quantity}; {@code resourceType} is carried verbatim (the consumer already knows it per topic).
 */
public record ReservationDeltaPayload(
        @NotNull
        UUID reservationId,
        UUID bookingId,
        String resourceType,

        @NotNull
        UUID resourceId,
        int quantity
) {}
