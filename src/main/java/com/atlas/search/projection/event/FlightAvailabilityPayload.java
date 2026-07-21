package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Resource-facing flight availability event (inventory-events.yaml FlightAvailabilityPayload, ADR-0008).
 * Copied from inventory-service. Search sets {@code FlightProjection.reserved} to the <b>absolute</b>
 * {@code reserved} value, applied only if {@code version ≥} the stored version (last-writer-wins).
 */
public record FlightAvailabilityPayload(
        @NotNull UUID reservationId, UUID bookingId, @NotNull UUID resourceId, int reserved, long version) {}
