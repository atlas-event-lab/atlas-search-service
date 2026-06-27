package com.atlas.search.projection.repository.model;

import com.atlas.search.projection.dto.ImageDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HotelRoomResult(
    UUID hotelId,
    String hotelName,
    String city,
    String country,
    Integer rating,
    UUID roomTypeId,
    String roomName,
    Integer maxOccupancy,
    BigDecimal pricePerNight,
    String currency,
    Integer available,
    List<String> amenities,
    List<ImageDto> hotelImages,
    List<ImageDto> roomImages
) { }
