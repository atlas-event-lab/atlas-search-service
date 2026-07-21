package com.atlas.search.search.dto;

import com.atlas.search.projection.dto.ImageDto;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

public record HotelSearchResponse(
        Integer page, Integer size, Long totalElements, Integer totalPages, List<HotelGroup> content) {

    @Builder
    public record HotelGroup(
            UUID id,
            String name,
            String city,
            String country,
            Integer rating,
            List<String> amenities,
            List<ImageDto> images,
            List<RoomDto> rooms) {}

    @Builder
    public record RoomDto(
            UUID roomTypeId,
            String name,
            Integer maxOccupancy,
            MoneyDto pricePerNight,
            Integer roomsAvailable,
            List<ImageDto> images) {}
}
