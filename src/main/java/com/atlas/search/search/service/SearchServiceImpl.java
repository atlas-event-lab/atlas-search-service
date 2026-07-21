package com.atlas.search.search.service;

import com.atlas.search.config.HotelSearchProperties;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.FlightSpecification;
import com.atlas.search.projection.repository.HotelSearchCustomRepository;
import com.atlas.search.projection.repository.model.HotelRoomResult;
import com.atlas.search.search.dto.FlightOffer;
import com.atlas.search.search.dto.FlightSearchRequest;
import com.atlas.search.search.dto.FlightSearchRequest.FlightSortOption;
import com.atlas.search.search.dto.FlightSearchResponse;
import com.atlas.search.search.dto.HotelSearchRequest;
import com.atlas.search.search.dto.HotelSearchRequest.HotelSortOption;
import com.atlas.search.search.dto.HotelSearchResponse;
import com.atlas.search.search.dto.HotelSearchResponse.HotelGroup;
import com.atlas.search.search.dto.HotelSearchResponse.RoomDto;
import com.atlas.search.search.dto.MoneyDto;
import com.atlas.search.search.exception.SearchValidationException;
import com.atlas.search.shared.exception.FieldErrorDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search service — live reads of index projections (read_model.md).
 * No snapshot persistence, no TTL (ADR-0002). Offers are computed per request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final FlightProjectionRepository flightProjectionRepository;
    private final HotelSearchCustomRepository hotelSearchCustomRepository;
    private final HotelSearchProperties hotelSearchProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FlightSearchResponse searchFlights(FlightSearchRequest criteria) {
        validateFlightCriteria(criteria);

        Specification<FlightProjection> spec = FlightSpecification.withCriteria(criteria);

        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), mapFlightSort(criteria.getSort()));

        Page<FlightProjection> page = flightProjectionRepository.findAll(spec, pageable);

        // Availability is folded into FlightProjection (ADR-0009): available = capacity − reserved.
        List<FlightOffer> offers = page.getContent().stream()
                .map(flightProjection -> toFlightOffer(flightProjection, flightProjection.getAvailable()))
                .toList();

        log.info("SearchFlights - flights found = {}", page.getTotalElements());
        return new FlightSearchResponse(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), offers);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelSearchResponse searchHotels(HotelSearchRequest criteria) {

        validateHotelCriteria(criteria);

        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), mapHotelSort(criteria.getSort()));

        Page<HotelRoomResult> page = hotelSearchCustomRepository.search(criteria, pageable);

        Map<UUID, HotelGroup> hotelGroupMap = new LinkedHashMap<>();

        page.getContent().forEach(rowResult -> hotelGroupMap
                .computeIfAbsent(rowResult.hotelId(), id -> HotelGroup.builder()
                        .id(rowResult.hotelId())
                        .name(rowResult.hotelName())
                        .city(rowResult.city())
                        .country(rowResult.country())
                        .rating(rowResult.rating())
                        .amenities(rowResult.amenities())
                        .images(rowResult.hotelImages())
                        .rooms(new ArrayList<>())
                        .build())
                .rooms()
                .add(RoomDto.builder()
                        .roomTypeId(rowResult.roomTypeId())
                        .name(rowResult.roomName())
                        .maxOccupancy(rowResult.maxOccupancy())
                        .pricePerNight(new MoneyDto(rowResult.pricePerNight(), rowResult.currency()))
                        .images(rowResult.roomImages())
                        .roomsAvailable(rowResult.available())
                        .build()));

        log.info("SearchHotels - rooms found = {}", page.getTotalElements());
        return new HotelSearchResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                new ArrayList<>(hotelGroupMap.values()));
    }

    private Sort mapHotelSort(HotelSortOption sort) {
        if (sort == null) {
            return Sort.by("rating").descending();
        }

        return switch (sort) {
            case PRICE -> Sort.by("roomTypes.pricePerNight").ascending();
            case RATING -> Sort.by("rating").descending();
        };
    }

    private Sort mapFlightSort(FlightSortOption sort) {
        if (sort == null) {
            return Sort.by("departureTime").ascending();
        }

        return switch (sort) {
            case FlightSortOption.PRICE -> Sort.by("basePrice").ascending();
            case FlightSortOption.DEPARTURE_TIME -> Sort.by("departureTime").ascending();
            case FlightSortOption.DURATION -> Sort.by("durationMinutes").ascending();
        };
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateFlightCriteria(FlightSearchRequest c) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (c.getOrigin() != null && c.getDestination() != null && c.getOrigin().equalsIgnoreCase(c.getDestination())) {
            errors.add(new FieldErrorDetail("destination", "origin and destination must differ"));
        }
        if (c.getDepartureDate() != null && c.getDepartureDate().isBefore(LocalDate.now(clock))) {
            errors.add(new FieldErrorDetail("departureDate", "departureDate must be today or in the future"));
        }
        int totalPax = (c.getAdults() != null ? c.getAdults() : 0) + c.getChildren() + c.getInfants();
        if (totalPax > 9) {
            errors.add(
                    new FieldErrorDetail("adults", "total passengers (adults + children + infants) must not exceed 9"));
        }

        if (!errors.isEmpty()) {
            log.error("Search criteria validation failed");
            throw new SearchValidationException("Search criteria validation failed", errors);
        }
    }

    private void validateHotelCriteria(HotelSearchRequest criteria) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (criteria.getCheckIn() != null && criteria.getCheckIn().isBefore(LocalDate.now(clock))) {
            errors.add(new FieldErrorDetail("checkIn", "checkIn must be today or in the future"));
        }
        if (criteria.getCheckIn() != null
                && criteria.getCheckOut() != null
                && !criteria.getCheckOut().isAfter(criteria.getCheckIn())) {
            errors.add(new FieldErrorDetail("checkOut", "checkOut must be after checkIn"));
        }
        if (criteria.getCheckIn() != null
                && criteria.getCheckOut() != null
                && criteria.getCheckOut().isAfter(criteria.getCheckIn())) {
            long nights = ChronoUnit.DAYS.between(criteria.getCheckIn(), criteria.getCheckOut());
            if (nights > hotelSearchProperties.maxStayNights()) {
                errors.add(new FieldErrorDetail(
                        "checkOut",
                        "stay length (" + nights + " nights) exceeds the maximum of "
                                + hotelSearchProperties.maxStayNights()));
            }
        }

        if (criteria.getMinPrice() != null
                && criteria.getMaxPrice() != null
                && criteria.getMinPrice().compareTo(criteria.getMaxPrice()) > 0) {
            errors.add(new FieldErrorDetail("minPrice", "minPrice must be less than maxPrice"));
        }

        if (!errors.isEmpty()) {
            throw new SearchValidationException("Search criteria validation failed", errors);
        }
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private FlightOffer toFlightOffer(FlightProjection projection, int available) {
        return new FlightOffer(
                projection.getId(),
                projection.getAirline(),
                projection.getOrigin(),
                projection.getDestination(),
                projection.getDepartureTime(),
                projection.getArrivalTime(),
                projection.getDurationMinutes(),
                projection.getStops(),
                new MoneyDto(projection.getBasePrice(), projection.getCurrency()),
                available);
    }
}
