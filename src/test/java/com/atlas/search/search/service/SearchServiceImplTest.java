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
import com.atlas.search.search.dto.TripSearchRequest;
import com.atlas.search.search.dto.TripSearchResponse;
import com.atlas.search.search.entity.TripItemType;
import com.atlas.search.search.entity.TripOffer;
import com.atlas.search.search.exception.SearchValidationException;
import com.atlas.search.search.exception.TripOfferExpiredException;
import com.atlas.search.search.exception.TripOfferNotFoundException;
import com.atlas.search.search.repository.TripOfferRepository;
import com.atlas.search.search.scheduler.TripOfferSweepProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceImplTest {

    @Mock FlightProjectionRepository flightRepo;
    @Mock HotelProjectionRepository hotelRepo;
    @Mock AvailabilityProjectionRepository availRepo;
    @Mock TripOfferRepository tripOfferRepo;
    @Mock TripOfferSweepProperties sweepProperties;
    @Mock Clock clock;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    SearchServiceImpl service;

    private static final Instant NOW = Instant.parse("2026-06-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(sweepProperties.getTtl()).thenReturn(Duration.ofMinutes(15));
        when(tripOfferRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void search_rejectsWhenOriginEqualsDestination() {
        TripSearchRequest req = baseRequest();
        req.setDestination("JFK");
        req.setOrigin("JFK");

        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(SearchValidationException.class)
                .hasMessageContaining("validation failed");
    }

    @Test
    void search_rejectsWhenDepartureDateIsInThePast() {
        TripSearchRequest req = baseRequest();
        req.setDepartureDate(TODAY.minusDays(1));

        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(SearchValidationException.class);
    }

    @Test
    void search_rejectsWhenReturnDateBeforeDepartureDate() {
        TripSearchRequest req = baseRequest();
        req.setReturnDate(TODAY.minusDays(1));

        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(SearchValidationException.class);
    }

    @Test
    void search_rejectsWhenTotalPaxExceedsNine() {
        TripSearchRequest req = baseRequest();
        req.setAdults(5);
        req.setChildren(3);
        req.setInfants(2); // total = 10

        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(SearchValidationException.class);
    }

    // ── Flight-only one-way ───────────────────────────────────────────────────

    @Test
    void search_returnsFlightOnlyOffers_whenOneWay() {
        TripSearchRequest req = baseRequest(); // no returnDate
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(eq("JFK"), eq("LAX"), eq(TODAY), eq(ProjectionStatus.ACTIVE)))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(10));

        TripSearchResponse response = service.search(req);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).hotels()).isEqualTo(0);
        assertThat(response.content().get(0).flights()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    // ── Pricing ───────────────────────────────────────────────────────────────

    @Test
    void search_computesFlightTotalAsBasePriceTimesPaxRequiringSeat() {
        // A2: infants are lap infants and do NOT count towards paxRequiringSeat
        TripSearchRequest req = baseRequest();
        req.setAdults(2);
        req.setChildren(1);
        req.setInfants(1); // lap infant, not counted

        FlightProjection flight = sampleFlight(); // basePrice = 100.00

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(10));

        TripSearchResponse response = service.search(req);

        // paxRequiringSeat = 2 + 1 = 3 (infants excluded per A2)
        BigDecimal expectedTotal = new BigDecimal("300.00");
        assertThat(response.content().get(0).total().amount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void search_computesRoundTripTotalIncludingHotel() {
        TripSearchRequest req = baseRequest();
        req.setReturnDate(TODAY.plusDays(3));
        req.setAdults(2);
        req.setRooms(1);

        FlightProjection flight = sampleFlight(); // basePrice = 100.00
        HotelProjection hotel = sampleHotel();
        HotelRoomType roomType = sampleRoomType(hotel); // pricePerNight = 80.00, maxOccupancy=2

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(10));
        when(hotelRepo.findActiveInCityWithRating(any(), any(), anyInt()))
                .thenReturn(List.of(hotel));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, roomType.getRoomTypeId()))
                .thenReturn(availableSlots(5));

        TripSearchResponse response = service.search(req);

        // Contains flight-only + flight+hotel offers
        // flight-only: 100 * 2 = 200.00
        // flight+hotel: 200 + 80*3*1 = 200 + 240 = 440.00
        // sorted by PRICE: 200.00 first, then 440.00
        assertThat(response.content()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.content().get(0).total().amount()).isEqualByComparingTo("200.00");
        assertThat(response.content().get(1).total().amount()).isEqualByComparingTo("440.00");
    }

    // ── Availability filtering ─────────────────────────────────────────────────

    @Test
    void search_excludesSoldOutFlights() {
        TripSearchRequest req = baseRequest();
        req.setAdults(3);

        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(2)); // only 2 available, need 3

        TripSearchResponse response = service.search(req);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
    }

    @Test
    void search_excludesDisabledFlights() {
        TripSearchRequest req = baseRequest();
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));

        AvailabilityProjection disabledAvail = new AvailabilityProjection();
        disabledAvail.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
        disabledAvail.setCapacity(100);
        disabledAvail.setReserved(0);
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(Optional.of(disabledAvail));

        TripSearchResponse response = service.search(req);

        assertThat(response.content()).isEmpty();
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Test
    void search_sortsByPriceAscendingByDefault() {
        // adults=1 so total = basePrice × 1 for easy assertion
        TripSearchRequest req = baseRequest();
        req.setAdults(1);

        FlightProjection cheap = sampleFlight();
        cheap.setBasePrice(new BigDecimal("50.00"));
        FlightProjection expensive = sampleFlight();
        expensive.setId(UUID.randomUUID());
        expensive.setBasePrice(new BigDecimal("200.00"));

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(expensive, cheap)); // intentionally out of order
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), eq(cheap.getId())))
                .thenReturn(availableSlots(10));
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), eq(expensive.getId())))
                .thenReturn(availableSlots(10));

        TripSearchResponse response = service.search(req);

        // Sorted by total PRICE ascending: 50.00 before 200.00
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).total().amount()).isEqualByComparingTo("50.00");
        assertThat(response.content().get(1).total().amount()).isEqualByComparingTo("200.00");
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    void search_paginatesResults() {
        TripSearchRequest req = baseRequest();
        req.setPage(0);
        req.setSize(1);

        FlightProjection f1 = sampleFlight();
        FlightProjection f2 = sampleFlight();
        f2.setId(UUID.randomUUID());

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(f1, f2));
        when(availRepo.findByResourceTypeAndResourceId(eq(ResourceType.FLIGHT), any()))
                .thenReturn(availableSlots(10));

        TripSearchResponse response = service.search(req);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    // ── getTrip ───────────────────────────────────────────────────────────────

    @Test
    void getTrip_returnsDetailForValidOffer() {
        UUID tripId = UUID.randomUUID();
        TripOffer offer = buildPersistedOffer(tripId, NOW.plus(10, ChronoUnit.MINUTES));

        when(tripOfferRepo.findByIdWithItems(tripId)).thenReturn(Optional.of(offer));

        var detail = service.getTrip(tripId);

        assertThat(detail.tripId()).isEqualTo(tripId);
    }

    @Test
    void getTrip_throws404WhenUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(tripOfferRepo.findByIdWithItems(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTrip(unknown))
                .isInstanceOf(TripOfferNotFoundException.class);
    }

    @Test
    void getTrip_throws410WhenExpired() {
        UUID tripId = UUID.randomUUID();
        TripOffer expired = buildPersistedOffer(tripId, NOW.minus(1, ChronoUnit.MINUTES));

        when(tripOfferRepo.findByIdWithItems(tripId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getTrip(tripId))
                .isInstanceOf(TripOfferExpiredException.class);
    }

    // ── Offer persistence ─────────────────────────────────────────────────────

    @Test
    void search_persistsOffersWithSearchIdAndTTL() {
        TripSearchRequest req = baseRequest();
        FlightProjection flight = sampleFlight();

        when(flightRepo.findByOriginDestinationAndDate(any(), any(), any(), any()))
                .thenReturn(List.of(flight));
        when(availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flight.getId()))
                .thenReturn(availableSlots(10));

        service.search(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TripOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripOfferRepo).saveAll(captor.capture());

        List<TripOffer> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSearchId()).isNotNull();
        assertThat(saved.get(0).getExpiresAt()).isEqualTo(NOW.plus(15, ChronoUnit.MINUTES));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TripSearchRequest baseRequest() {
        TripSearchRequest req = new TripSearchRequest();
        req.setOrigin("JFK");
        req.setDestination("LAX");
        req.setDepartureDate(TODAY);
        req.setAdults(2);
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
        h.setCity("LAX");
        h.setCountry("US");
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

    private TripOffer buildPersistedOffer(UUID tripId, Instant expiresAt) {
        TripOffer offer = new TripOffer();
        offer.setId(tripId);
        offer.setSearchId(UUID.randomUUID());
        offer.setSearchCriteria("{}");
        offer.setTotalAmount(new BigDecimal("200.00"));
        offer.setCurrency("USD");
        offer.setFlightCount(1);
        offer.setHotelCount(0);
        offer.setExpiresAt(expiresAt);
        return offer;
    }

}
