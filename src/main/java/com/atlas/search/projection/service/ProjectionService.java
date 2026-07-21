package com.atlas.search.projection.service;

import com.atlas.search.projection.event.FlightAvailabilityPayload;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelAvailabilityPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.util.UUID;

/** Maintains the Search Service index projections from catalog and inventory events (ADR-0009). */
public interface ProjectionService {

    void upsertFlight(UUID eventId, ConsumerEventType eventType, FlightCatalogPayload payload);

    void disableFlight(UUID eventId, UUID flightId);

    void upsertHotel(UUID eventId, ConsumerEventType eventType, HotelCatalogPayload payload);

    void disableHotel(UUID eventId, UUID hotelId);

    /**
     * Applies an absolute flight availability update: set {@code FlightProjection.reserved} to
     * {@code payload.reserved} iff {@code payload.version ≥} the stored version (last-writer-wins,
     * ADR-0008). Idempotent under redelivery.
     */
    void applyFlightAvailability(FlightAvailabilityPayload payload);

    /**
     * Applies an absolute per-night hotel availability update: for each night, set {@code reserved}
     * to the payload value iff {@code payload.version ≥} the stored version (last-writer-wins).
     */
    void applyHotelAvailability(HotelAvailabilityPayload payload);
}
