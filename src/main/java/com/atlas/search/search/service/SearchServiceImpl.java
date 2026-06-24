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
import com.atlas.search.search.dto.MoneyDto;
import com.atlas.search.search.dto.TripDetailDto;
import com.atlas.search.search.dto.TripItemDto;
import com.atlas.search.search.dto.TripSearchRequest;
import com.atlas.search.search.dto.TripSearchResponse;
import com.atlas.search.search.dto.TripSummaryDto;
import com.atlas.search.search.entity.TripItemType;
import com.atlas.search.search.entity.TripOffer;
import com.atlas.search.search.entity.TripOfferItem;
import com.atlas.search.search.exception.SearchValidationException;
import com.atlas.search.search.exception.TripOfferExpiredException;
import com.atlas.search.search.exception.TripOfferNotFoundException;
import com.atlas.search.search.repository.TripOfferRepository;
import com.atlas.search.search.scheduler.TripOfferSweepProperties;
import com.atlas.search.shared.exception.FieldErrorDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final String DEFAULT_CURRENCY = "USD";

    private final FlightProjectionRepository flightRepo;
    private final HotelProjectionRepository hotelRepo;
    private final AvailabilityProjectionRepository availRepo;
    private final TripOfferRepository tripOfferRepo;
    private final TripOfferSweepProperties sweepProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TripSearchResponse search(TripSearchRequest criteria) {
        validate(criteria);

        // A2: paxRequiringSeat = adults + children (infants are lap — no seat)
        int paxRequiringSeat = criteria.getAdults() + criteria.getChildren();
        Integer nights = criteria.getReturnDate() != null
                ? (int) ChronoUnit.DAYS.between(criteria.getDepartureDate(), criteria.getReturnDate())
                : null;

        // Step 1: Query and filter available flights
        List<FlightProjection> flights = flightRepo.findByOriginDestinationAndDate(
                criteria.getOrigin().toUpperCase(),
                criteria.getDestination().toUpperCase(),
                criteria.getDepartureDate(),
                ProjectionStatus.ACTIVE);

        if (criteria.getAirlines() != null && !criteria.getAirlines().isEmpty()) {
            flights = flights.stream()
                    .filter(f -> criteria.getAirlines().contains(f.getAirline()))
                    .toList();
        }

        // A1: stops/nonStop filters are inert until Flight Service publishes segment count
        flights = flights.stream()
                .filter(f -> isAvailable(ResourceType.FLIGHT, f.getId(), paxRequiringSeat))
                .toList();

        // Step 2: Query available hotel room types (only for round-trips)
        List<RoomOption> roomOptions = List.of();
        if (nights != null) {
            roomOptions = findAvailableRoomOptions(criteria.getDestination(), criteria.getHotelRating(),
                    criteria.getRooms(), criteria.getAdults(), criteria.getChildren());
        }

        // Step 3: Assemble offers
        String searchCriteriaJson = serialize(criteria);
        List<TripOffer> offers = assembleOffers(flights, roomOptions, criteria, paxRequiringSeat, nights, searchCriteriaJson);

        // Step 4: Apply price range filter
        if (criteria.getMinPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.getTotalAmount().compareTo(criteria.getMinPrice()) >= 0)
                    .toList();
        }
        if (criteria.getMaxPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.getTotalAmount().compareTo(criteria.getMaxPrice()) <= 0)
                    .toList();
        }

        // Step 5: Sort
        offers = sort(offers, criteria.getSort());

        // Step 6: Paginate
        int page = criteria.getPage();
        int size = criteria.getSize();
        long totalElements = offers.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<TripOffer> pageOffers = offers.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();

        // Step 7: Persist the page's offers as frozen snapshots
        UUID searchId = UUID.randomUUID();
        Instant expiresAt = Instant.now(clock).plus(sweepProperties.getTtl());
        pageOffers.forEach(o -> {
            o.setSearchId(searchId);
            o.setExpiresAt(expiresAt);
        });
        tripOfferRepo.saveAll(pageOffers);

        log.info("Search completed: searchId={}, total={}, page={}/{}, returned={}",
                searchId, totalElements, page, totalPages, pageOffers.size());

        // Step 8: Return summary page
        List<TripSummaryDto> summaries = pageOffers.stream()
                .map(o -> toSummary(o, criteria))
                .toList();
        return new TripSearchResponse(page, size, totalElements, totalPages, summaries);
    }

    @Override
    @Transactional(readOnly = true)
    public TripDetailDto getTrip(UUID tripId) {
        TripOffer offer = tripOfferRepo.findByIdWithItems(tripId)
                .orElseThrow(() -> new TripOfferNotFoundException(tripId));

        if (offer.isExpired(Instant.now(clock))) {
            throw new TripOfferExpiredException(tripId);
        }

        return toDetail(offer);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validate(TripSearchRequest c) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (c.getOrigin() != null && c.getDestination() != null
                && c.getOrigin().equalsIgnoreCase(c.getDestination())) {
            errors.add(new FieldErrorDetail("destination", "origin and destination must differ"));
        }
        if (c.getDepartureDate() != null && c.getDepartureDate().isBefore(LocalDate.now(clock))) {
            errors.add(new FieldErrorDetail("departureDate", "departureDate must be today or in the future"));
        }
        if (c.getReturnDate() != null && c.getDepartureDate() != null
                && c.getReturnDate().isBefore(c.getDepartureDate())) {
            errors.add(new FieldErrorDetail("returnDate", "returnDate must be on or after departureDate"));
        }
        int totalPax = (c.getAdults() != null ? c.getAdults() : 0) + c.getChildren() + c.getInfants();
        if (totalPax > 9) {
            errors.add(new FieldErrorDetail("adults", "total passengers (adults + children + infants) must not exceed 9"));
        }

        if (!errors.isEmpty()) {
            throw new SearchValidationException("Search criteria validation failed", errors);
        }
    }

    // ── Availability check ────────────────────────────────────────────────────

    private boolean isAvailable(ResourceType type, UUID resourceId, int required) {
        return availRepo.findByResourceTypeAndResourceId(type, resourceId)
                .map(a -> a.getStatus() == AvailabilityProjection.AvailabilityStatus.ACTIVE
                        && a.getAvailable() >= required)
                .orElse(false);
    }

    // ── Hotel room option discovery ───────────────────────────────────────────

    private List<RoomOption> findAvailableRoomOptions(String city, Integer minRating,
                                                       int rooms, int adults, int children) {
        int effectiveMinRating = minRating != null ? minRating : 1;
        // Per-room occupancy check: each room must fit ceil((adults+children)/rooms) guests
        int minOccupancyPerRoom = (int) Math.ceil((double) (adults + children) / rooms);

        List<HotelProjection> hotels = hotelRepo.findActiveInCityWithRating(
                city, ProjectionStatus.ACTIVE, effectiveMinRating);

        List<RoomOption> options = new ArrayList<>();
        for (HotelProjection hotel : hotels) {
            for (HotelRoomType rt : hotel.getRoomTypes()) {
                if (rt.getMaxOccupancy() < minOccupancyPerRoom) continue;
                if (!isAvailable(ResourceType.HOTEL, rt.getRoomTypeId(), rooms)) continue;
                options.add(new RoomOption(hotel, rt));
            }
        }
        return options;
    }

    // ── Offer assembly ────────────────────────────────────────────────────────

    private List<TripOffer> assembleOffers(List<FlightProjection> flights,
                                           List<RoomOption> roomOptions,
                                           TripSearchRequest criteria,
                                           int paxRequiringSeat,
                                           Integer nights,
                                           String searchCriteriaJson) {
        List<TripOffer> offers = new ArrayList<>();
        boolean isRoundTrip = nights != null;

        for (FlightProjection flight : flights) {
            // Always include flight-only offer
            offers.add(buildFlightOnlyOffer(flight, paxRequiringSeat, searchCriteriaJson, criteria.getAdults()));

            // Add flight + hotel combinations for round-trips
            if (isRoundTrip) {
                for (RoomOption room : roomOptions) {
                    offers.add(buildOfferWithHotel(flight, room, paxRequiringSeat, nights,
                            criteria.getRooms(), searchCriteriaJson));
                }
            }
        }

        return offers;
    }

    private TripOffer buildFlightOnlyOffer(FlightProjection flight, int paxRequiringSeat,
                                           String searchCriteriaJson, int adults) {
        // A4: taxes=serviceFee=discount=0 in MVP; total = flightTotal
        BigDecimal flightTotal = flight.getBasePrice()
                .multiply(BigDecimal.valueOf(paxRequiringSeat))
                .setScale(SCALE, ROUNDING);

        TripOffer offer = new TripOffer();
        offer.setId(UUID.randomUUID());
        offer.setSearchCriteria(searchCriteriaJson);
        offer.setTotalAmount(flightTotal);
        offer.setCurrency(flight.getCurrency());
        offer.setFlightCount(1);
        offer.setHotelCount(0);

        TripOfferItem flightItem = buildFlightItem(flight, paxRequiringSeat, offer);
        offer.getItems().add(flightItem);

        return offer;
    }

    private TripOffer buildOfferWithHotel(FlightProjection flight, RoomOption room,
                                          int paxRequiringSeat, int nights, int rooms,
                                          String searchCriteriaJson) {
        BigDecimal flightTotal = flight.getBasePrice()
                .multiply(BigDecimal.valueOf(paxRequiringSeat))
                .setScale(SCALE, ROUNDING);

        BigDecimal hotelTotal = room.roomType().getPricePerNight()
                .multiply(BigDecimal.valueOf((long) nights * rooms))
                .setScale(SCALE, ROUNDING);

        // A4: total = flightTotal + hotelTotal (taxes/fees/discount = 0)
        BigDecimal total = flightTotal.add(hotelTotal).setScale(SCALE, ROUNDING);

        TripOffer offer = new TripOffer();
        offer.setId(UUID.randomUUID());
        offer.setSearchCriteria(searchCriteriaJson);
        offer.setTotalAmount(total);
        offer.setCurrency(flight.getCurrency());
        offer.setFlightCount(1);
        offer.setHotelCount(1);

        TripOfferItem flightItem = buildFlightItem(flight, paxRequiringSeat, offer);
        TripOfferItem hotelItem = buildHotelItem(room, nights, rooms, offer, flight.getCurrency());
        offer.getItems().add(flightItem);
        offer.getItems().add(hotelItem);

        return offer;
    }

    private TripOfferItem buildFlightItem(FlightProjection flight, int paxRequiringSeat, TripOffer offer) {
        BigDecimal lineTotal = flight.getBasePrice()
                .multiply(BigDecimal.valueOf(paxRequiringSeat))
                .setScale(SCALE, ROUNDING);

        TripOfferItem item = new TripOfferItem();
        item.setId(UUID.randomUUID());
        item.setTripOffer(offer);
        item.setType(TripItemType.FLIGHT);
        item.setResourceId(flight.getId());
        item.setQuantity(paxRequiringSeat);
        item.setUnitPriceAmount(flight.getBasePrice().setScale(SCALE, ROUNDING));
        item.setUnitPriceCurrency(flight.getCurrency());
        item.setLineTotalAmount(lineTotal);
        item.setLineTotalCurrency(flight.getCurrency());
        item.setAirline(flight.getAirline());
        item.setDepartureTime(flight.getDepartureTime());
        item.setArrivalTime(flight.getArrivalTime());
        item.setStops(flight.getStops());
        return item;
    }

    private TripOfferItem buildHotelItem(RoomOption room, int nights, int rooms,
                                          TripOffer offer, String currency) {
        // quantity = rooms × nights (total room-nights as per read_model.md TripOfferItem spec)
        int quantity = rooms * nights;
        BigDecimal lineTotal = room.roomType().getPricePerNight()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(SCALE, ROUNDING);

        TripOfferItem item = new TripOfferItem();
        item.setId(UUID.randomUUID());
        item.setTripOffer(offer);
        item.setType(TripItemType.HOTEL);
        item.setResourceId(room.roomType().getRoomTypeId());
        item.setQuantity(quantity);
        item.setUnitPriceAmount(room.roomType().getPricePerNight().setScale(SCALE, ROUNDING));
        item.setUnitPriceCurrency(room.roomType().getCurrency());
        item.setLineTotalAmount(lineTotal);
        item.setLineTotalCurrency(room.roomType().getCurrency());
        item.setHotelName(room.hotel().getName());
        item.setRating(room.hotel().getRating());
        item.setNights(nights);
        return item;
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    private List<TripOffer> sort(List<TripOffer> offers, TripSearchRequest.SortOption sortOption) {
        TripSearchRequest.SortOption effective = sortOption != null ? sortOption : TripSearchRequest.SortOption.PRICE;
        Comparator<TripOffer> comparator = switch (effective) {
            case PRICE -> Comparator.comparing(TripOffer::getTotalAmount);
            case DEPARTURE_TIME -> Comparator.comparing(o -> flightDepartureTime(o));
            case DURATION -> Comparator.comparingInt(o -> flightDurationMinutes(o));
        };
        return offers.stream().sorted(comparator).toList();
    }

    private Instant flightDepartureTime(TripOffer offer) {
        return offer.getItems().stream()
                .filter(i -> i.getType() == TripItemType.FLIGHT)
                .findFirst()
                .map(TripOfferItem::getDepartureTime)
                .orElse(Instant.EPOCH);
    }

    private int flightDurationMinutes(TripOffer offer) {
        return offer.getItems().stream()
                .filter(i -> i.getType() == TripItemType.FLIGHT)
                .findFirst()
                .map(i -> (int) ChronoUnit.MINUTES.between(i.getDepartureTime(), i.getArrivalTime()))
                .orElse(0);
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private TripSummaryDto toSummary(TripOffer offer, TripSearchRequest criteria) {
        Optional<TripOfferItem> flightItem = offer.getItems().stream()
                .filter(i -> i.getType() == TripItemType.FLIGHT)
                .findFirst();

        return new TripSummaryDto(
                offer.getId(),
                criteria.getOrigin(),
                criteria.getDestination(),
                criteria.getDepartureDate(),
                criteria.getReturnDate(),
                flightItem.map(TripOfferItem::getAirline).orElse(null),
                flightItem.map(i -> (int) ChronoUnit.MINUTES.between(i.getDepartureTime(), i.getArrivalTime())).orElse(0),
                flightItem.map(i -> i.getStops() != null ? i.getStops() : 0).orElse(0),
                offer.getFlightCount(),
                offer.getHotelCount(),
                new MoneyDto(offer.getTotalAmount(), offer.getCurrency()),
                offer.getExpiresAt()
        );
    }

    private TripDetailDto toDetail(TripOffer offer) {
        List<TripItemDto> items = offer.getItems().stream()
                .map(i -> new TripItemDto(
                        i.getType().name(),
                        i.getResourceId(),
                        i.getQuantity(),
                        new MoneyDto(i.getUnitPriceAmount(), i.getUnitPriceCurrency()),
                        new MoneyDto(i.getLineTotalAmount(), i.getLineTotalCurrency()),
                        i.getAirline(),
                        i.getDepartureTime(),
                        i.getArrivalTime(),
                        i.getStops(),
                        i.getHotelName(),
                        i.getRating(),
                        i.getNights()
                ))
                .toList();

        return new TripDetailDto(
                offer.getId(),
                items,
                new MoneyDto(offer.getTotalAmount(), offer.getCurrency()),
                offer.getCreatedAt(),
                offer.getExpiresAt()
        );
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String serialize(TripSearchRequest criteria) {
        try {
            return objectMapper.writeValueAsString(criteria);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Internal projection of an available hotel room type for offer assembly. */
    private record RoomOption(HotelProjection hotel, HotelRoomType roomType) {}
}
