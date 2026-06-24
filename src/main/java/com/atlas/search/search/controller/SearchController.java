package com.atlas.search.search.controller;

import com.atlas.search.search.dto.TripDetailDto;
import com.atlas.search.search.dto.TripSearchRequest;
import com.atlas.search.search.dto.TripSearchResponse;
import com.atlas.search.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes the Search API (contracts/openapi/search.yaml).
 * Holds no business logic (API-003); delegates entirely to {@link SearchService}.
 */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /search/trips — Search bookable trips. Anonymous (no JWT required).
     */
    @PostMapping("/search/trips")
    public ResponseEntity<TripSearchResponse> searchTrips(
        @RequestBody @Valid TripSearchRequest criteria
    ) {
        return ResponseEntity.ok(searchService.search(criteria));
    }

    /**
     * GET /trips/{tripId} — Resolve a TripOffer snapshot. Anonymous.
     * Returns 200 while TTL holds, 410 after expiry, 404 if unknown.
     */
    @GetMapping("/trips/{tripId}")
    public ResponseEntity<TripDetailDto> getTrip(@PathVariable UUID tripId) {
        return ResponseEntity.ok(searchService.getTrip(tripId));
    }
}
