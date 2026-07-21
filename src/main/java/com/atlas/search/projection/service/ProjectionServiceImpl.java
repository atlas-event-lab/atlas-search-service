package com.atlas.search.projection.service;

import com.atlas.search.config.HotelSearchProperties;
import com.atlas.search.projection.entity.AvailabilityStatus;
import com.atlas.search.projection.entity.ConsumedEvent;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.RoomTypeNightAvailabilityProjection;
import com.atlas.search.projection.event.FlightAvailabilityPayload;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelAvailabilityPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.NightAvailability;
import com.atlas.search.projection.event.RoomTypeEvent;
import com.atlas.search.projection.repository.ConsumedEventRepository;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelRoomTypeRepository;
import com.atlas.search.projection.repository.RoomTypeNightAvailabilityProjectionRepository;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionServiceImpl implements ProjectionService {

    private final FlightProjectionRepository flightProjectionRepository;
    private final HotelRoomTypeRepository hotelRoomTypeRepository;
    private final RoomTypeNightAvailabilityProjectionRepository roomTypeAvailabilityRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final HotelSearchProperties properties;
    private final Clock clock;

    // ── Flight catalog ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void upsertFlight(UUID eventId, ConsumerEventType eventType, FlightCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) {
            return;
        }

        UUID flightId = payload.flightId();
        FlightProjection flight = flightProjectionRepository.findById(flightId).orElseGet(() -> {
            FlightProjection f = new FlightProjection();
            f.setId(flightId);
            f.setReserved(0);
            f.setVersion(0);
            return f;
        });

        flight.setAirline(payload.airlineName());
        flight.setOrigin(payload.originAirportCode());
        flight.setDestination(payload.destinationAirportCode());
        flight.setDepartureTime(payload.departureTime());
        flight.setArrivalTime(payload.arrivalTime());
        flight.setDurationMinutes(computeDuration(flight.getDepartureTime(), flight.getArrivalTime()));
        flight.setBasePrice(payload.basePrice().amount());
        flight.setCurrency(payload.basePrice().currency());
        flight.setCapacity(payload.totalSeats()); // reserved/version preserved (inventory-owned)
        flight.setStatus(ProjectionStatus.ACTIVE);
        flightProjectionRepository.save(flight);

        markConsumed(eventId, eventType);
        log.info("Upserted FlightProjection: flightId={}", flightId);
    }

    @Override
    @Transactional
    public void disableFlight(UUID eventId, UUID flightId) {
        if (alreadyConsumed(eventId, ConsumerEventType.FLIGHT_DELETED)) {
            return;
        }

        flightProjectionRepository.findById(flightId).ifPresent(f -> {
            f.setStatus(ProjectionStatus.WITHDRAWN);
            flightProjectionRepository.save(f);
        });
        markConsumed(eventId, ConsumerEventType.FLIGHT_DELETED);
        log.info("Disabled FlightProjection: flightId={}", flightId);
    }

    // ── Hotel catalog (per-night calendar materialization) ─────────────────────

    @Override
    @Transactional
    public void upsertHotel(UUID eventId, ConsumerEventType eventType, HotelCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) {
            return;
        }

        UUID hotelId = payload.hotelId();
        HotelProjection hotel = hotelRoomTypeRepository.findById(hotelId).orElseGet(() -> {
            HotelProjection projection = new HotelProjection();
            projection.setId(hotelId);
            return projection;
        });

        // Room types present before this event — used to reconcile removals.
        Set<UUID> previousRoomTypeIds =
                hotel.getRoomTypes().stream().map(HotelRoomType::getId).collect(Collectors.toSet());

        hotel.setName(payload.name());
        hotel.setCity(payload.city());
        hotel.setCountry(payload.country());
        hotel.setRating(payload.rating());
        hotel.setStatus(ProjectionStatus.ACTIVE);
        hotel.setAmenities(payload.amenities());
        hotel.setImages(payload.images());

        hotel.getRoomTypes().clear();
        for (RoomTypeEvent roomTypeEvent : payload.roomTypes()) {
            HotelRoomType roomType = new HotelRoomType();
            roomType.setId(roomTypeEvent.roomTypeId());
            roomType.setHotel(hotel);
            roomType.setName(roomTypeEvent.name());
            roomType.setPricePerNight(roomTypeEvent.pricePerNight().amount());
            roomType.setCurrency(roomTypeEvent.pricePerNight().currency());
            roomType.setMaxOccupancy(roomTypeEvent.maxOccupancy());
            roomType.setImages(roomTypeEvent.images());
            hotel.getRoomTypes().add(roomType);
        }
        hotelRoomTypeRepository.save(hotel);

        materializeHotelCalendar(payload.roomTypes(), previousRoomTypeIds);

        markConsumed(eventId, eventType);
        log.info("Upserted HotelProjection + calendar: hotelId={}, horizonDays={}", hotelId, properties.horizonDays());
    }

    @Override
    @Transactional
    public void disableHotel(UUID eventId, UUID hotelId) {
        if (alreadyConsumed(eventId, ConsumerEventType.HOTEL_DELETED)) {
            return;
        }

        hotelRoomTypeRepository.findById(hotelId).ifPresent(hotel -> {
            hotel.setStatus(ProjectionStatus.WITHDRAWN);
            hotelRoomTypeRepository.save(hotel);
            List<UUID> roomTypeIds =
                    hotel.getRoomTypes().stream().map(HotelRoomType::getId).toList();
            disableFutureNights(roomTypeIds);
        });
        markConsumed(eventId, ConsumerEventType.HOTEL_DELETED);
        log.info("Disabled HotelProjection + future nights: hotelId={}", hotelId);
    }

    // ── Availability (absolute + version-guarded) ──────────────────────────────

    @Override
    @Transactional
    public void applyFlightAvailability(FlightAvailabilityPayload payload) {
        flightProjectionRepository
                .findById(payload.resourceId())
                .ifPresentOrElse(
                        flight -> {
                            if (payload.version() >= flight.getVersion()) {
                                flight.setReserved(payload.reserved());
                                flight.setVersion(payload.version());
                                flightProjectionRepository.save(flight);
                            } else {
                                log.info(
                                        "Dropping stale flight availability: "
                                                + "flightId={}, incomingVersion={} < stored={}",
                                        payload.resourceId(),
                                        payload.version(),
                                        flight.getVersion());
                            }
                        },
                        () -> log.warn(
                                "FlightProjection not found for availability update: flightId={}",
                                payload.resourceId()));
    }

    @Override
    @Transactional
    public void applyHotelAvailability(HotelAvailabilityPayload payload) {
        for (NightAvailability night : payload.nights()) {
            roomTypeAvailabilityRepository
                    .findByResourceIdAndStayDate(payload.roomTypeId(), night.stayDate())
                    .ifPresentOrElse(
                            row -> {
                                if (payload.version() >= row.getVersion()) {
                                    row.setReserved(night.reserved());
                                    row.setVersion(payload.version());
                                    roomTypeAvailabilityRepository.save(row);
                                } else {
                                    log.info(
                                            "Dropping stale hotel night availability: roomTypeId={}, night={}, "
                                                    + "incomingVersion={} < stored={}",
                                            payload.roomTypeId(),
                                            night.stayDate(),
                                            payload.version(),
                                            row.getVersion());
                                }
                            },
                            () -> log.warn(
                                    "Room-type night not found for availability update: roomTypeId={}, night={}",
                                    payload.roomTypeId(),
                                    night.stayDate()));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Materializes/updates the horizon night rows for present room types and disables removed ones. */
    private void materializeHotelCalendar(List<RoomTypeEvent> roomTypes, Set<UUID> previousRoomTypeIds) {
        LocalDate today = LocalDate.now(clock);
        LocalDate endExclusive = today.plusDays(properties.horizonDays());

        Set<UUID> presentIds = roomTypes.stream().map(RoomTypeEvent::roomTypeId).collect(Collectors.toSet());

        Map<UUID, List<RoomTypeNightAvailabilityProjection>> existingByRoomType = presentIds.isEmpty()
                ? Map.of()
                : roomTypeAvailabilityRepository
                        .findByResourceIdInAndStayDateGreaterThanEqual(presentIds, today)
                        .stream()
                        .collect(Collectors.groupingBy(RoomTypeNightAvailabilityProjection::getResourceId));

        for (RoomTypeEvent roomType : roomTypes) {
            List<RoomTypeNightAvailabilityProjection> existing =
                    existingByRoomType.getOrDefault(roomType.roomTypeId(), List.of());
            Set<LocalDate> existingDates = new HashSet<>();
            for (RoomTypeNightAvailabilityProjection row : existing) {
                existingDates.add(row.getStayDate());
                if (row.getStayDate().isBefore(endExclusive)) {
                    row.setCapacity(roomType.totalRooms()); // capacity is catalog-owned; keep reserved/version/status
                }
            }
            for (LocalDate date = today; date.isBefore(endExclusive); date = date.plusDays(1)) {
                if (!existingDates.contains(date)) {
                    roomTypeAvailabilityRepository.save(new RoomTypeNightAvailabilityProjection(
                            UUID.randomUUID(),
                            roomType.roomTypeId(),
                            date,
                            roomType.totalRooms(),
                            0,
                            AvailabilityStatus.ACTIVE,
                            0));
                }
            }
        }

        // Reconcile removals: room types present before but not now → DISABLE their future nights.
        Set<UUID> removed = new HashSet<>(previousRoomTypeIds);
        removed.removeAll(presentIds);
        if (!removed.isEmpty()) {
            disableFutureNights(List.copyOf(removed));
        }
    }

    private void disableFutureNights(List<UUID> roomTypeIds) {
        if (roomTypeIds.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        for (RoomTypeNightAvailabilityProjection row :
                roomTypeAvailabilityRepository.findByResourceIdInAndStayDateGreaterThanEqual(roomTypeIds, today)) {
            if (row.getStatus() == AvailabilityStatus.ACTIVE) {
                row.setStatus(AvailabilityStatus.DISABLED);
            }
        }
    }

    private boolean alreadyConsumed(UUID eventId, ConsumerEventType eventType) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate event: eventId={}, type={}", eventId, eventType);
            return true;
        }
        return false;
    }

    private void markConsumed(UUID eventId, ConsumerEventType eventType) {
        try {
            consumedEventRepository.save(new ConsumedEvent(eventId, eventType));
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted first — idempotent, safe to ignore.
            log.error("Consumed event already recorded (race): eventId={}", eventId);
        }
    }

    private int computeDuration(Instant departure, Instant arrival) {
        return (int) java.time.Duration.between(departure, arrival).toMinutes();
    }
}
