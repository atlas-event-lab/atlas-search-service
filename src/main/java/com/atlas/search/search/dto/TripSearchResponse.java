package com.atlas.search.search.dto;

import java.util.List;

/** Paginated search results (search.yaml — TripSearchResponse + common/pagination.yaml). */
public record TripSearchResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<TripSummaryDto> content
) {}
