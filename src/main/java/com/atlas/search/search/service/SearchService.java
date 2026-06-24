package com.atlas.search.search.service;

import com.atlas.search.search.dto.TripDetailDto;
import com.atlas.search.search.dto.TripSearchRequest;
import com.atlas.search.search.dto.TripSearchResponse;

import java.util.UUID;

public interface SearchService {

    TripSearchResponse search(TripSearchRequest criteria);

    TripDetailDto getTrip(UUID tripId);
}
