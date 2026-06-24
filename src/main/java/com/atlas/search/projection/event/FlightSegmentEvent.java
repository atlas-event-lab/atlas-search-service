package com.atlas.search.projection.event;

import java.time.Instant;

/**
 * Denormalized flight leg inside catalog event payloads (flight-events.yaml FlightSegment).
 * Copied from flight-service so Search consumes a strongly typed payload.
 */
public record FlightSegmentEvent(
        int sequence,
        String originAirportCode,
        String destinationAirportCode,
        Instant departureTime,
        Instant arrivalTime
) {}
