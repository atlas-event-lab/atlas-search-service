package com.atlas.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.search.config.HotelSearchProperties;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelSearchCustomRepository;
import com.atlas.search.projection.repository.model.HotelRoomResult;
import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchRequest.FlightSortOption;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchRequest.HotelSortOption;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.dto.HotelSearchResponse.RoomDto;
import com.atlas.search.search.exception.SearchValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
  private static final int MAX_STAY = 30;

  @Mock
  private FlightProjectionRepository flightProjectionRepository;
  @Mock
  private HotelSearchCustomRepository hotelSearchCustomRepository;

  private SearchServiceImpl searchService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    var properties = new HotelSearchProperties(365, 7, MAX_STAY);
    searchService = new SearchServiceImpl(
        flightProjectionRepository, hotelSearchCustomRepository, properties, clock);
  }

  // ── searchFlights ────────────────────────────────────────────────────────

  @Test
  void searchFlights_returnsOffersWithAvailability_foldedIntoFlightProjection() {
    FlightSearchRequest criteria = validFlightRequest();

    UUID flightId = UUID.randomUUID();
    FlightProjection flight = new FlightProjection();
    flight.setId(flightId);
    flight.setAirline("Delta");
    flight.setOrigin("JFK");
    flight.setDestination("LAX");
    flight.setDepartureTime(Instant.parse("2026-07-10T10:00:00Z"));
    flight.setArrivalTime(Instant.parse("2026-07-10T13:00:00Z"));
    flight.setDurationMinutes(180);
    flight.setStops(0);
    flight.setBasePrice(new BigDecimal("250.00"));
    flight.setCurrency("USD");
    flight.setCapacity(10);
    flight.setReserved(3);

    Page<FlightProjection> page = new PageImpl<>(List.of(flight), Pageable.ofSize(20), 1);
    when(flightProjectionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    FlightSearchResponse response = searchService.searchFlights(criteria);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().flightId()).isEqualTo(flightId);
    assertThat(response.content().getFirst().available()).isEqualTo(7);
    assertThat(response.content().getFirst().basePrice().amount()).isEqualByComparingTo("250.00");
    assertThat(response.totalElements()).isEqualTo(1);
  }

  @Test
  void searchFlights_availabilityZero_whenReservedMeetsCapacity() {
    FlightSearchRequest criteria = validFlightRequest();

    FlightProjection flight = new FlightProjection();
    flight.setId(UUID.randomUUID());
    flight.setDepartureTime(Instant.parse("2026-07-10T10:00:00Z"));
    flight.setArrivalTime(Instant.parse("2026-07-10T13:00:00Z"));
    flight.setBasePrice(new BigDecimal("100.00"));
    flight.setCurrency("USD");
    flight.setCapacity(5);
    flight.setReserved(5);

    when(flightProjectionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(flight)));

    FlightSearchResponse response = searchService.searchFlights(criteria);

    assertThat(response.content().getFirst().available()).isZero();
  }

  @Test
  void searchFlights_throwsValidationException_whenOriginEqualsDestination() {
    FlightSearchRequest criteria = validFlightRequest();
    criteria.setOrigin("JFK");
    criteria.setDestination("jfk");

    assertThatThrownBy(() -> searchService.searchFlights(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("destination"));
  }

  @Test
  void searchFlights_throwsValidationException_whenDepartureDateInPast() {
    FlightSearchRequest criteria = validFlightRequest();
    criteria.setDepartureDate(TODAY.minusDays(1));

    assertThatThrownBy(() -> searchService.searchFlights(criteria))
        .isInstanceOf(SearchValidationException.class);
  }

  @Test
  void searchFlights_throwsValidationException_whenTotalPassengersExceedsNine() {
    FlightSearchRequest criteria = validFlightRequest();
    criteria.setAdults(5);
    criteria.setChildren(3);
    criteria.setInfants(2);

    assertThatThrownBy(() -> searchService.searchFlights(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("adults"));
  }

  @Test
  void searchFlights_sortsByBasePriceAscending_whenSortIsPrice() {
    FlightSearchRequest criteria = validFlightRequest();
    criteria.setSort(FlightSortOption.PRICE);
    stubEmptyFlightPage();

    searchService.searchFlights(criteria);

    Sort sort = capturedFlightPageable().getSort();
    assertThat(sort.getOrderFor("basePrice")).isNotNull();
    assertThat(Objects.requireNonNull(sort.getOrderFor("basePrice")).isAscending()).isTrue();
  }

  // ── searchHotels ─────────────────────────────────────────────────────────

  @Test
  void searchHotels_groupsRoomsByHotel_whenMultipleRoomsSameHotel() {
    HotelSearchRequest criteria = validHotelRequest();

    UUID hotelId = UUID.randomUUID();
    UUID room1 = UUID.randomUUID();
    UUID room2 = UUID.randomUUID();

    HotelRoomResult result1 = new HotelRoomResult(
        hotelId, "Grand Hotel", "Lima", "Peru", 4,
        room1, "Deluxe", 2, new BigDecimal("100.00"), "USD", 3,
        List.of("wifi"), List.of(), List.of());
    HotelRoomResult result2 = new HotelRoomResult(
        hotelId, "Grand Hotel", "Lima", "Peru", 4,
        room2, "Suite", 4, new BigDecimal("200.00"), "USD", 1,
        List.of("wifi"), List.of(), List.of());

    when(hotelSearchCustomRepository.search(eq(criteria), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(result1, result2)));

    HotelSearchResponse response = searchService.searchHotels(criteria);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().id()).isEqualTo(hotelId);
    assertThat(response.content().getFirst().rooms()).hasSize(2);
    assertThat(response.content().getFirst().rooms())
        .extracting(RoomDto::roomTypeId)
        .containsExactlyInAnyOrder(room1, room2);
  }

  @Test
  void searchHotels_throwsValidationException_whenCheckInInPast() {
    HotelSearchRequest criteria = validHotelRequest();
    criteria.setCheckIn(TODAY.minusDays(1));

    assertThatThrownBy(() -> searchService.searchHotels(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("checkIn"));
  }

  @Test
  void searchHotels_throwsValidationException_whenCheckOutNotAfterCheckIn() {
    HotelSearchRequest criteria = validHotelRequest();
    criteria.setCheckIn(TODAY.plusDays(5));
    criteria.setCheckOut(TODAY.plusDays(5));

    assertThatThrownBy(() -> searchService.searchHotels(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("checkOut"));
  }

  @Test
  void searchHotels_throwsValidationException_whenStayExceedsMaxStay() {
    HotelSearchRequest criteria = validHotelRequest();
    criteria.setCheckIn(TODAY.plusDays(1));
    criteria.setCheckOut(TODAY.plusDays(1 + MAX_STAY + 1)); // MAX_STAY + 1 nights

    assertThatThrownBy(() -> searchService.searchHotels(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("checkOut"));
  }

  @Test
  void searchHotels_throwsValidationException_whenMinPriceGreaterThanMaxPrice() {
    HotelSearchRequest criteria = validHotelRequest();
    criteria.setMinPrice(new BigDecimal("300"));
    criteria.setMaxPrice(new BigDecimal("100"));

    assertThatThrownBy(() -> searchService.searchHotels(criteria))
        .isInstanceOf(SearchValidationException.class)
        .satisfies(ex -> assertThat(((SearchValidationException) ex).getErrors())
            .extracting("field")
            .contains("minPrice"));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private FlightSearchRequest validFlightRequest() {
    FlightSearchRequest request = new FlightSearchRequest();
    request.setOrigin("JFK");
    request.setDestination("LAX");
    request.setDepartureDate(TODAY.plusDays(10));
    request.setAdults(2);
    request.setChildren(0);
    request.setInfants(0);
    request.setPage(0);
    request.setSize(20);
    return request;
  }

  private HotelSearchRequest validHotelRequest() {
    HotelSearchRequest request = new HotelSearchRequest();
    request.setCity("Lima");
    request.setCheckIn(TODAY.plusDays(5));
    request.setCheckOut(TODAY.plusDays(8));
    request.setRooms(1);
    request.setGuests(2);
    request.setPage(0);
    request.setSize(20);
    return request;
  }

  private void stubEmptyFlightPage() {
    when(flightProjectionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
  }

  private Pageable capturedFlightPageable() {
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(flightProjectionRepository).findAll(any(Specification.class), captor.capture());
    return captor.getValue();
  }
}
