package com.atlas.search.projection.event;

import com.atlas.search.projection.dto.ImageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
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
        MoneyEvent pricePerNight,

        List<ImageDto> images
) {}
