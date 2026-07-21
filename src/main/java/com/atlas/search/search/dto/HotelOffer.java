package com.atlas.search.search.dto;

import java.util.UUID;

/** One bookable hotel room type, live from the projections (search.yaml HotelOffer). */
public record HotelOffer(
        UUID hotelId,
        String name,
        String city,
        String country,
        int rating,
        UUID roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        MoneyDto pricePerNight,
        int available) {}
