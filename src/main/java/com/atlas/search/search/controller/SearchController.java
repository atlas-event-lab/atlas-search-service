package com.atlas.search.search.controller;

import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the Search API (search.yaml). Anonymous flight/hotel offer queries
 * as live reads of projections (no snapshot, no TTL — ADR-0002).
 * Holds no business logic (API-003); delegates entirely to {@link SearchService}.
 */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** GET /search/flights — search bookable flights. Anonymous. */
    @GetMapping("/search/flights")
    public ResponseEntity<FlightSearchResponse> searchFlights(@Valid FlightSearchRequest criteria) {
        return ResponseEntity.ok(searchService.searchFlights(criteria));
    }

    /** GET /search/hotels — search bookable hotels. Anonymous. */
    @GetMapping("/search/hotels")
    public ResponseEntity<HotelSearchResponse> searchHotels(@Valid HotelSearchRequest criteria) {
        return ResponseEntity.ok(searchService.searchHotels(criteria));
    }
}
