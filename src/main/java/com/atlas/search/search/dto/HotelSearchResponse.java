package com.atlas.search.search.dto;

import java.util.List;

/** Paginated hotel search results (search.yaml HotelSearchResponse). */
public record HotelSearchResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<HotelOffer> content
) {}
