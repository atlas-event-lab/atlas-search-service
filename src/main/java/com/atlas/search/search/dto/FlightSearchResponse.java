package com.atlas.search.search.dto;

import java.util.List;

/** Paginated flight search results (search.yaml FlightSearchResponse). */
public record FlightSearchResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<FlightOffer> content
) {}
