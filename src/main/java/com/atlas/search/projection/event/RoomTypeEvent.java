package com.atlas.search.projection.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Denormalized room type inside catalog event payloads (hotel-events.yaml RoomType).
 * Copied from hotel-service.
 */
public record RoomTypeEvent(
        @NotNull
        UUID roomTypeId,
        String name,
        int totalRooms,
        int maxOccupancy,

        @Valid
        MoneyEvent pricePerNight
) {}
