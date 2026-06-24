package com.atlas.search.projection.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code FlightCreated} / {@code FlightUpdated} (flight-events.yaml FlightCatalogPayload).
 * Copied faithfully from flight-service so Search consumes a strongly typed envelope.
 */
public record FlightCatalogPayload(
        @NotNull
        UUID flightId,
        String flightNumber,
        String airlineCode,
        String airlineName,
        String originAirportCode,
        String destinationAirportCode,
        Instant departureTime,
        Instant arrivalTime,
        int totalSeats,

        @Valid
        @NotNull
        MoneyEvent basePrice,

        @Valid
        List<FlightSegmentEvent> segments
) {}
