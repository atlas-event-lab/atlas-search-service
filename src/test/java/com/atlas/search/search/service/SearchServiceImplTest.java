package com.atlas.search.search.service;

import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.repository.AvailabilityProjectionRepository;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelProjectionRepository;
import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.exception.SearchValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceImplTest {

    @Mock FlightProjectionRepository flightRepo;
    @Mock HotelProjectionRepository hotelRepo;
    @Mock AvailabilityProjectionRepository availRepo;
    @Mock Clock clock;

    @InjectMocks
    SearchServiceImpl service;

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    // ── Flight validation ────────────────────────────────────────────────────

    @Test
    void searchFlights_rejectsWhenOriginEqualsDestination() {
        FlightSearchRequest req = flightRequest();
        req.setOrigin("JFK");
        req.setDestination("JFK");

        assertThatThrownBy(() -> service.searchFlights(req))
                .isInstanceOf(SearchValidationException.class);
    }

    @Test
    void searchFlights_rejectsWhenDepartureDateIsInThePast() {
        FlightSearchRequest req = flightRequest();
        req.setDepartureDate(TODAY.minusDays(1));

        assertThatThrownBy(() -> service.searchFlights(req))
                .isInstanceOf(SearchValidationException.class);
    }

    @Test
    void searchFlights_rejectsWhenTotalPaxExceedsNine() {
        FlightSearchRequest req = flightRequest();
        req.setAdults(5);
        req.setChildren(3);
        req.setInfants(2);

        assertThatThrownBy(() -> service.searchFlights(req))
                .isInstanceOf(SearchValidationException.class);
    }

    // ── Flight search results ────────────────────────────────────────────────

    @Test
    void searchFlights_returnsFlightOffers() {
        FlightSearchRequest req = flightRequest();
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(eq("JFK"), eq("LAX"), eq(TODAY), eq(ProjectionStatus.ACTIVE)))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(10));

        FlightSearchResponse response = service.searchFlights(req);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).flightId()).isEqualTo(flight.getId());
        assertThat(response.content().get(0).basePrice().amount()).isEqualByComparingTo("100.00");
        assertThat(response.content().get(0).available()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void searchFlights_excludesSoldOutFlights() {
        FlightSearchRequest req = flightRequest();
        req.setAdults(3);
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(2));

        FlightSearchResponse response = service.searchFlights(req);

        assertThat(response.content()).isEmpty();
    }

    @Test
    void searchFlights_excludesDisabledFlights() {
        FlightSearchRequest req = flightRequest();
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));

        AvailabilityProjection disabled = new AvailabilityProjection();
        disabled.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
        disabled.setCapacity(100);
        disabled.setReserved(0);
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(Optional.of(disabled));

        FlightSearchResponse response = service.searchFlights(req);

        assertThat(response.content()).isEmpty();
    }

    @Test
    void searchFlights_sortsByPriceAscendingByDefault() {
        FlightSearchRequest req = flightRequest();
        req.setAdults(1);

        FlightProjection cheap = sampleFlight();
        cheap.setBasePrice(new BigDecimal("50.00"));
        FlightProjection expensive = sampleFlight();
        expensive.setId(UUID.randomUUID());
        expensive.setBasePrice(new BigDecimal("200.00"));

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(expensive, cheap));
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), eq(cheap.getId())))
                .thenReturn(availableSlots(10));
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), eq(expensive.getId())))
                .thenReturn(availableSlots(10));

        FlightSearchResponse response = service.searchFlights(req);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).basePrice().amount()).isEqualByComparingTo("50.00");
        assertThat(response.content().get(1).basePrice().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void searchFlights_paginatesResults() {
        FlightSearchRequest req = flightRequest();
        req.setPage(0);
        req.setSize(1);

        FlightProjection f1 = sampleFlight();
        FlightProjection f2 = sampleFlight();
        f2.setId(UUID.randomUUID());

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(f1, f2));
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), any()))
                .thenReturn(availableSlots(10));

        FlightSearchResponse response = service.searchFlights(req);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    // ── Hotel validation ─────────────────────────────────────────────────────

    @Test
    void searchHotels_rejectsWhenCheckOutNotAfterCheckIn() {
        HotelSearchRequest req = hotelRequest();
        req.setCheckOut(TODAY);

        assertThatThrownBy(() -> service.searchHotels(req))
                .isInstanceOf(SearchValidationException.class);
    }

    @Test
    void searchHotels_rejectsWhenCheckInIsInThePast() {
        HotelSearchRequest req = hotelRequest();
        req.setCheckIn(TODAY.minusDays(1));
        req.setCheckOut(TODAY);

        assertThatThrownBy(() -> service.searchHotels(req))
                .isInstanceOf(SearchValidationException.class);
    }

    // ── Hotel search results ─────────────────────────────────────────────────

    @Test
    void searchHotels_returnsHotelOffers() {
        HotelSearchRequest req = hotelRequest();
        HotelProjection hotel = sampleHotel();
        HotelRoomType rt = sampleRoomType(hotel);

        when(hotelRepo.findActiveInCityWithRating(any(), any(), anyInt()))
                .thenReturn(List.of(hotel));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, rt.getRoomTypeId()))
                .thenReturn(availableSlots(5));

        HotelSearchResponse response = service.searchHotels(req);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).hotelId()).isEqualTo(hotel.getId());
        assertThat(response.content().get(0).roomTypeId()).isEqualTo(rt.getRoomTypeId());
        assertThat(response.content().get(0).pricePerNight().amount()).isEqualByComparingTo("80.00");
        assertThat(response.content().get(0).available()).isEqualTo(5);
    }

    @Test
    void searchHotels_excludesSoldOutRoomTypes() {
        HotelSearchRequest req = hotelRequest();
        req.setRooms(10);
        HotelProjection hotel = sampleHotel();
        HotelRoomType rt = sampleRoomType(hotel);

        when(hotelRepo.findActiveInCityWithRating(any(), any(), anyInt()))
                .thenReturn(List.of(hotel));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, rt.getRoomTypeId()))
                .thenReturn(availableSlots(5));

        HotelSearchResponse response = service.searchHotels(req);

        assertThat(response.content()).isEmpty();
    }

    @Test
    void searchHotels_filtersRoomTypesByGuestOccupancy() {
        HotelSearchRequest req = hotelRequest();
        req.setGuests(5);
        HotelProjection hotel = sampleHotel();
        sampleRoomType(hotel); // maxOccupancy=2, too small

        when(hotelRepo.findActiveInCityWithRating(any(), any(), anyInt()))
                .thenReturn(List.of(hotel));

        HotelSearchResponse response = service.searchHotels(req);

        assertThat(response.content()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FlightSearchRequest flightRequest() {
        FlightSearchRequest req = new FlightSearchRequest();
        req.setOrigin("JFK");
        req.setDestination("LAX");
        req.setDepartureDate(TODAY);
        req.setAdults(2);
        return req;
    }

    private HotelSearchRequest hotelRequest() {
        HotelSearchRequest req = new HotelSearchRequest();
        req.setCity("Lima");
        req.setCheckIn(TODAY);
        req.setCheckOut(TODAY.plusDays(3));
        req.setRooms(1);
        return req;
    }

    private FlightProjection sampleFlight() {
        FlightProjection f = new FlightProjection();
        f.setId(UUID.randomUUID());
        f.setAirline("AA");
        f.setOrigin("JFK");
        f.setDestination("LAX");
        f.setDepartureTime(TODAY.atTime(8, 0).toInstant(ZoneOffset.UTC));
        f.setArrivalTime(TODAY.atTime(11, 0).toInstant(ZoneOffset.UTC));
        f.setDurationMinutes(180);
        f.setBasePrice(new BigDecimal("100.00"));
        f.setCurrency("USD");
        f.setStatus(ProjectionStatus.ACTIVE);
        return f;
    }

    private HotelProjection sampleHotel() {
        HotelProjection h = new HotelProjection();
        h.setId(UUID.randomUUID());
        h.setName("Grand Hotel");
        h.setCity("Lima");
        h.setCountry("PE");
        h.setRating(4);
        h.setStatus(ProjectionStatus.ACTIVE);
        return h;
    }

    private HotelRoomType sampleRoomType(HotelProjection hotel) {
        HotelRoomType rt = new HotelRoomType();
        rt.setId(UUID.randomUUID());
        rt.setRoomTypeId(UUID.randomUUID());
        rt.setHotel(hotel);
        rt.setName("Standard");
        rt.setPricePerNight(new BigDecimal("80.00"));
        rt.setCurrency("USD");
        rt.setMaxOccupancy(2);
        hotel.getRoomTypes().add(rt);
        return rt;
    }

    private Optional<AvailabilityProjection> availableSlots(int capacity) {
        AvailabilityProjection avail = new AvailabilityProjection();
        avail.setCapacity(capacity);
        avail.setReserved(0);
        avail.setStatus(AvailabilityProjection.AvailabilityStatus.ACTIVE);
        return Optional.of(avail);
    }
}
