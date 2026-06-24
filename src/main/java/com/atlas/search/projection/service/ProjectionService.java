package com.atlas.search.projection.service;

import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.shared.messaging.ConsumerEventType;

import java.util.UUID;

/** Maintains the Search Service index projections from catalog and inventory events. */
public interface ProjectionService {

    void upsertFlight(UUID eventId, ConsumerEventType eventType, FlightCatalogPayload payload);

    void disableFlight(UUID eventId, UUID flightId);

    void upsertHotel(UUID eventId, ConsumerEventType eventType, HotelCatalogPayload payload);

    void disableHotel(UUID eventId, UUID hotelId);

    void incrementReserved(UUID eventId, ConsumerEventType eventType, ResourceType resourceType,
                           UUID resourceId, int quantity);

    void decrementReserved(UUID eventId, ConsumerEventType eventType, ResourceType resourceType,
                           UUID resourceId, int quantity);
}
