package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Payload for {@code FlightDeleted} (flight-events.yaml FlightDeletedPayload). */
public record FlightDeletedPayload(@NotNull UUID flightId) {}
