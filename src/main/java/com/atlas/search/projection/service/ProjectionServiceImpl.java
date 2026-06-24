package com.atlas.search.projection.service;

import com.atlas.search.projection.entity.AvailabilityProjection;
import com.atlas.search.projection.entity.ConsumedEvent;
import com.atlas.search.projection.entity.FlightProjection;
import com.atlas.search.projection.entity.HotelProjection;
import com.atlas.search.projection.entity.HotelRoomType;
import com.atlas.search.projection.entity.ProjectionStatus;
import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.RoomTypeEvent;
import com.atlas.search.projection.repository.AvailabilityProjectionRepository;
import com.atlas.search.projection.repository.ConsumedEventRepository;
import com.atlas.search.projection.repository.FlightProjectionRepository;
import com.atlas.search.projection.repository.HotelProjectionRepository;
import com.atlas.search.shared.messaging.ConsumerEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionServiceImpl implements ProjectionService {

    private final FlightProjectionRepository flightRepo;
    private final HotelProjectionRepository hotelRepo;
    private final AvailabilityProjectionRepository availRepo;
    private final ConsumedEventRepository consumedRepo;

    @Override
    @Transactional
    public void upsertFlight(UUID eventId, ConsumerEventType eventType, FlightCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) return;

        UUID flightId = payload.flightId();
        FlightProjection flight = flightRepo.findById(flightId).orElseGet(() -> {
            FlightProjection f = new FlightProjection();
            f.setId(flightId);
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
        flight.setStatus(ProjectionStatus.ACTIVE);
        flightRepo.save(flight);

        upsertFlightAvailability(flightId, payload.totalSeats());

        markConsumed(eventId, eventType);
        log.info("Upserted FlightProjection: flightId={}", flightId);
    }

    @Override
    @Transactional
    public void disableFlight(UUID eventId, UUID flightId) {
        if (alreadyConsumed(eventId, ConsumerEventType.FLIGHT_DELETED)) return;

        flightRepo.findById(flightId).ifPresent(f -> {
            f.setStatus(ProjectionStatus.WITHDRAWN);
            flightRepo.save(f);
        });
        availRepo.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId).ifPresent(a -> {
            a.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
            availRepo.save(a);
        });
        markConsumed(eventId, ConsumerEventType.FLIGHT_DELETED);
        log.info("Disabled FlightProjection: flightId={}", flightId);
    }

    @Override
    @Transactional
    public void upsertHotel(UUID eventId, ConsumerEventType eventType, HotelCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) return;

        UUID hotelId = payload.hotelId();
        HotelProjection hotel = hotelRepo.findById(hotelId).orElseGet(() -> {
            HotelProjection h = new HotelProjection();
            h.setId(hotelId);
            return h;
        });

        hotel.setName(payload.name());
        hotel.setCity(payload.city());
        hotel.setCountry(payload.country());
        hotel.setRating(payload.rating());
        hotel.setStatus(ProjectionStatus.ACTIVE);

        // Replace room types; orphanRemoval handles deletion of removed ones.
        hotel.getRoomTypes().clear();
        for (RoomTypeEvent rt : payload.roomTypes()) {
            HotelRoomType roomType = new HotelRoomType();
            roomType.setId(UUID.randomUUID());
            roomType.setHotel(hotel);
            roomType.setRoomTypeId(rt.roomTypeId());
            roomType.setName(rt.name());
            roomType.setPricePerNight(rt.pricePerNight().amount());
            roomType.setCurrency(rt.pricePerNight().currency());
            roomType.setMaxOccupancy(rt.maxOccupancy());
            hotel.getRoomTypes().add(roomType);

            upsertHotelRoomAvailability(rt.roomTypeId(), rt.totalRooms());
        }

        hotelRepo.save(hotel);
        markConsumed(eventId, eventType);
        log.info("Upserted HotelProjection: hotelId={}", hotelId);
    }

    @Override
    @Transactional
    public void disableHotel(UUID eventId, UUID hotelId) {
        if (alreadyConsumed(eventId, ConsumerEventType.HOTEL_DELETED)) return;

        hotelRepo.findById(hotelId).ifPresent(h -> {
            h.setStatus(ProjectionStatus.WITHDRAWN);
            h.getRoomTypes().forEach(rt ->
                    availRepo.findByResourceTypeAndResourceId(ResourceType.HOTEL, rt.getRoomTypeId())
                            .ifPresent(a -> {
                                a.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
                                availRepo.save(a);
                            })
            );
            hotelRepo.save(h);
        });
        markConsumed(eventId, ConsumerEventType.HOTEL_DELETED);
        log.info("Disabled HotelProjection: hotelId={}", hotelId);
    }

    @Override
    @Transactional
    public void incrementReserved(UUID eventId, ConsumerEventType eventType, ResourceType resourceType,
                                  UUID resourceId, int quantity) {
        if (alreadyConsumed(eventId, eventType)) return;

        availRepo.findByResourceTypeAndResourceId(resourceType, resourceId).ifPresentOrElse(a -> {
            a.setReserved(a.getReserved() + quantity);
            availRepo.save(a);
            log.debug("Incremented reserved: resourceType={}, resourceId={}, qty={}", resourceType, resourceId, quantity);
        }, () -> log.warn("AvailabilityProjection not found for increment: type={}, id={}", resourceType, resourceId));

        markConsumed(eventId, eventType);
    }

    @Override
    @Transactional
    public void decrementReserved(UUID eventId, ConsumerEventType eventType, ResourceType resourceType,
                                  UUID resourceId, int quantity) {
        if (alreadyConsumed(eventId, eventType)) return;

        availRepo.findByResourceTypeAndResourceId(resourceType, resourceId).ifPresentOrElse(a -> {
            a.setReserved(Math.max(0, a.getReserved() - quantity));
            availRepo.save(a);
            log.debug("Decremented reserved: resourceType={}, resourceId={}, qty={}", resourceType, resourceId, quantity);
        }, () -> log.warn("AvailabilityProjection not found for decrement: type={}, id={}", resourceType, resourceId));

        markConsumed(eventId, eventType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertFlightAvailability(UUID flightId, int totalSeats) {
        AvailabilityProjection avail = availRepo
                .findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId)
                .orElseGet(() -> {
                    AvailabilityProjection a = new AvailabilityProjection();
                    a.setId(UUID.randomUUID());
                    a.setResourceType(ResourceType.FLIGHT);
                    a.setResourceId(flightId);
                    a.setReserved(0);
                    return a;
                });
        avail.setCapacity(totalSeats);
        avail.setStatus(AvailabilityProjection.AvailabilityStatus.ACTIVE);
        availRepo.save(avail);
    }

    private void upsertHotelRoomAvailability(UUID roomTypeId, int totalRooms) {
        AvailabilityProjection avail = availRepo
                .findByResourceTypeAndResourceId(ResourceType.HOTEL, roomTypeId)
                .orElseGet(() -> {
                    AvailabilityProjection a = new AvailabilityProjection();
                    a.setId(UUID.randomUUID());
                    a.setResourceType(ResourceType.HOTEL);
                    a.setResourceId(roomTypeId);
                    a.setReserved(0);
                    return a;
                });
        avail.setCapacity(totalRooms);
        avail.setStatus(AvailabilityProjection.AvailabilityStatus.ACTIVE);
        availRepo.save(avail);
    }

    private boolean alreadyConsumed(UUID eventId, ConsumerEventType eventType) {
        if (consumedRepo.existsById(eventId)) {
            log.debug("Skipping duplicate event: eventId={}, type={}", eventId, eventType);
            return true;
        }
        return false;
    }

    private void markConsumed(UUID eventId, ConsumerEventType eventType) {
        try {
            consumedRepo.save(new ConsumedEvent(eventId, eventType));
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted first — idempotent, safe to ignore.
            log.debug("Consumed event already recorded (race): eventId={}", eventId);
        }
    }

    private int computeDuration(Instant departure, Instant arrival) {
        return (int) java.time.Duration.between(departure, arrival).toMinutes();
    }
}
