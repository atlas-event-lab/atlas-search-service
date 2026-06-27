package com.atlas.search.projection.event;

import com.atlas.search.projection.dto.ImageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code HotelCreated} / {@code HotelUpdated} (hotel-events.yaml HotelCatalogPayload).
 * Copied faithfully from hotel-service so Search consumes a strongly typed envelope.
 */
public record HotelCatalogPayload(
        @NotNull
        UUID hotelId,
        String name,
        String city,
        String country,
        int rating,

        @Valid
        @NotEmpty
        List<RoomTypeEvent> roomTypes,
        List<String> amenities,
        List<ImageDto> images
) {}
