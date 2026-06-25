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
import com.atlas.search.search.dto.FlightOffer;
import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelOffer;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.dto.MoneyDto;
import com.atlas.search.search.exception.SearchValidationException;
import com.atlas.search.shared.exception.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Search service — live reads of index projections (read_model.md).
 * No snapshot persistence, no TTL (ADR-0002). Offers are computed per request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final FlightProjectionRepository flightRepo;
    private final HotelProjectionRepository hotelRepo;
    private final AvailabilityProjectionRepository availRepo;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FlightSearchResponse searchFlights(FlightSearchRequest criteria) {
        validateFlightCriteria(criteria);

        int paxRequiringSeat = criteria.getAdults() + criteria.getChildren();

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

        List<FlightOffer> offers = flights.stream()
                .filter(f -> isAvailable(ResourceType.FLIGHT, f.getId(), paxRequiringSeat))
                .map(f -> toFlightOffer(f, availableCount(ResourceType.FLIGHT, f.getId())))
                .toList();

        if (criteria.getMinPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.basePrice().amount().compareTo(criteria.getMinPrice()) >= 0)
                    .toList();
        }
        if (criteria.getMaxPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.basePrice().amount().compareTo(criteria.getMaxPrice()) <= 0)
                    .toList();
        }

        offers = sortFlights(offers, criteria.getSort());

        return paginateFlights(offers, criteria.getPage(), criteria.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public HotelSearchResponse searchHotels(HotelSearchRequest criteria) {
        validateHotelCriteria(criteria);

        int effectiveMinRating = criteria.getHotelRating() != null ? criteria.getHotelRating() : 1;

        List<HotelProjection> hotels = hotelRepo.findActiveInCityWithRating(
                criteria.getCity(), ProjectionStatus.ACTIVE, effectiveMinRating);

        List<HotelOffer> offers = new ArrayList<>();
        for (HotelProjection hotel : hotels) {
            for (HotelRoomType rt : hotel.getRoomTypes()) {
                if (criteria.getGuests() != null && rt.getMaxOccupancy() < criteria.getGuests()) {
                    continue;
                }
                if (!isAvailable(ResourceType.HOTEL, rt.getRoomTypeId(), criteria.getRooms())) {
                    continue;
                }
                offers.add(toHotelOffer(hotel, rt, availableCount(ResourceType.HOTEL, rt.getRoomTypeId())));
            }
        }

        if (criteria.getMinPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.pricePerNight().amount().compareTo(criteria.getMinPrice()) >= 0)
                    .toList();
        }
        if (criteria.getMaxPrice() != null) {
            offers = offers.stream()
                    .filter(o -> o.pricePerNight().amount().compareTo(criteria.getMaxPrice()) <= 0)
                    .toList();
        }

        offers = sortHotels(offers, criteria.getSort());

        return paginateHotels(offers, criteria.getPage(), criteria.getSize());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateFlightCriteria(FlightSearchRequest c) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (c.getOrigin() != null && c.getDestination() != null
                && c.getOrigin().equalsIgnoreCase(c.getDestination())) {
            errors.add(new FieldErrorDetail("destination", "origin and destination must differ"));
        }
        if (c.getDepartureDate() != null && c.getDepartureDate().isBefore(LocalDate.now(clock))) {
            errors.add(new FieldErrorDetail("departureDate", "departureDate must be today or in the future"));
        }
        int totalPax = (c.getAdults() != null ? c.getAdults() : 0) + c.getChildren() + c.getInfants();
        if (totalPax > 9) {
            errors.add(new FieldErrorDetail("adults", "total passengers (adults + children + infants) must not exceed 9"));
        }

        if (!errors.isEmpty()) {
            throw new SearchValidationException("Search criteria validation failed", errors);
        }
    }

    private void validateHotelCriteria(HotelSearchRequest c) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (c.getCheckIn() != null && c.getCheckIn().isBefore(LocalDate.now(clock))) {
            errors.add(new FieldErrorDetail("checkIn", "checkIn must be today or in the future"));
        }
        if (c.getCheckIn() != null && c.getCheckOut() != null
                && !c.getCheckOut().isAfter(c.getCheckIn())) {
            errors.add(new FieldErrorDetail("checkOut", "checkOut must be after checkIn"));
        }

        if (!errors.isEmpty()) {
            throw new SearchValidationException("Search criteria validation failed", errors);
        }
    }

    // ── Availability ──────────────────────────────────────────────────────────

    private boolean isAvailable(ResourceType type, UUID resourceId, int required) {
        return availRepo.findByResourceTypeAndResourceId(type, resourceId)
                .map(a -> a.getStatus() == AvailabilityProjection.AvailabilityStatus.ACTIVE
                        && a.getAvailable() >= required)
                .orElse(false);
    }

    private int availableCount(ResourceType type, UUID resourceId) {
        return availRepo.findByResourceTypeAndResourceId(type, resourceId)
                .map(AvailabilityProjection::getAvailable)
                .orElse(0);
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private FlightOffer toFlightOffer(FlightProjection f, int available) {
        return new FlightOffer(
                f.getId(), f.getAirline(), f.getOrigin(), f.getDestination(),
                f.getDepartureTime(), f.getArrivalTime(), f.getDurationMinutes(), f.getStops(),
                new MoneyDto(f.getBasePrice(), f.getCurrency()), available);
    }

    private HotelOffer toHotelOffer(HotelProjection h, HotelRoomType rt, int available) {
        return new HotelOffer(
                h.getId(), h.getName(), h.getCity(), h.getCountry(), h.getRating(),
                rt.getRoomTypeId(), rt.getName(), rt.getMaxOccupancy(),
                new MoneyDto(rt.getPricePerNight(), rt.getCurrency()), available);
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    private List<FlightOffer> sortFlights(List<FlightOffer> offers, FlightSearchRequest.FlightSortOption sort) {
        FlightSearchRequest.FlightSortOption effective = sort != null ? sort : FlightSearchRequest.FlightSortOption.PRICE;
        Comparator<FlightOffer> comparator = switch (effective) {
            case PRICE -> Comparator.comparing(o -> o.basePrice().amount());
            case DEPARTURE_TIME -> Comparator.comparing(FlightOffer::departureTime);
            case DURATION -> Comparator.comparingInt(FlightOffer::durationMinutes);
        };
        return offers.stream().sorted(comparator).toList();
    }

    private List<HotelOffer> sortHotels(List<HotelOffer> offers, HotelSearchRequest.HotelSortOption sort) {
        HotelSearchRequest.HotelSortOption effective = sort != null ? sort : HotelSearchRequest.HotelSortOption.PRICE;
        Comparator<HotelOffer> comparator = switch (effective) {
            case PRICE -> Comparator.comparing(o -> o.pricePerNight().amount());
            case RATING -> Comparator.comparingInt(HotelOffer::rating).reversed();
        };
        return offers.stream().sorted(comparator).toList();
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private FlightSearchResponse paginateFlights(List<FlightOffer> offers, int page, int size) {
        long totalElements = offers.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<FlightOffer> pageContent = offers.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        return new FlightSearchResponse(page, size, totalElements, totalPages, pageContent);
    }

    private HotelSearchResponse paginateHotels(List<HotelOffer> offers, int page, int size) {
        long totalElements = offers.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<HotelOffer> pageContent = offers.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        return new HotelSearchResponse(page, size, totalElements, totalPages, pageContent);
    }
}
