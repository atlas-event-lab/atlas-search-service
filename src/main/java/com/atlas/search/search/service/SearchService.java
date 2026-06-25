package com.atlas.search.search.service;

import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchResponse;

/** Search queries — live reads of projections (no snapshots, no TTL). */
public interface SearchService {

    FlightSearchResponse searchFlights(FlightSearchRequest criteria);

    HotelSearchResponse searchHotels(HotelSearchRequest criteria);
}
