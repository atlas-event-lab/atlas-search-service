package com.atlas.search.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.service.SearchService;
import com.atlas.search.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SearchService searchService;

  @Test
  void searchFlights_returns200WithResults_whenCriteriaValid() throws Exception {
    when(searchService.searchFlights(any(FlightSearchRequest.class)))
        .thenReturn(new FlightSearchResponse(0, 20, 0, 0, java.util.List.of()));

    mockMvc.perform(get("/api/v1/search/flights")
            .param("origin", "JFK")
            .param("destination", "LAX")
            .param("departureDate", "2026-08-01")
            .param("adults", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));

    verify(searchService).searchFlights(any(FlightSearchRequest.class));
  }

  @Test
  void searchFlights_returns400_whenOriginMissing() throws Exception {
    mockMvc.perform(get("/api/v1/search/flights")
            .param("destination", "LAX")
            .param("departureDate", "2026-08-01")
            .param("adults", "2"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://atlas/errors/validation"));
  }

  @Test
  void searchHotels_returns200WithResults_whenCriteriaValid() throws Exception {
    when(searchService.searchHotels(any(HotelSearchRequest.class)))
        .thenReturn(new HotelSearchResponse(0, 20, 0L, 0, java.util.List.of()));

    mockMvc.perform(get("/api/v1/search/hotels")
            .param("city", "Lima")
            .param("checkIn", "2026-08-01")
            .param("checkOut", "2026-08-05")
            .param("rooms", "1")
            .param("guests", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0));

    verify(searchService).searchHotels(any(HotelSearchRequest.class));
  }

  @Test
  void searchHotels_returns400_whenCityMissing() throws Exception {
    mockMvc.perform(get("/api/v1/search/hotels")
            .param("checkIn", "2026-08-01")
            .param("checkOut", "2026-08-05")
            .param("rooms", "1")
            .param("guests", "2"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://atlas/errors/validation"));
  }
}
