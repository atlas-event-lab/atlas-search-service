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
import com.atlas.search.projection.repository.HotelRoomTypeRepository;
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

    private final FlightProjectionRepository flightProjectionRepository;
    private final HotelRoomTypeRepository hotelRoomTypeRepository;
    private final AvailabilityProjectionRepository availabilityProjectionRepository;
    private final ConsumedEventRepository consumedEventRepository;

    @Override
    @Transactional
    public void upsertFlight(UUID eventId, ConsumerEventType eventType, FlightCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) return;

        UUID flightId = payload.flightId();
        FlightProjection flight = flightProjectionRepository.findById(flightId).orElseGet(() -> {
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
        flightProjectionRepository.save(flight);

        upsertFlightAvailability(flightId, payload.totalSeats());

        markConsumed(eventId, eventType);
        log.info("Upserted FlightProjection: flightId={}", flightId);
    }

    @Override
    @Transactional
    public void disableFlight(UUID eventId, UUID flightId) {
        if (alreadyConsumed(eventId, ConsumerEventType.FLIGHT_DELETED)) return;

        flightProjectionRepository.findById(flightId).ifPresent(f -> {
            f.setStatus(ProjectionStatus.WITHDRAWN);
            flightProjectionRepository.save(f);
        });
        availabilityProjectionRepository.findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId).ifPresent(a -> {
            a.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
            availabilityProjectionRepository.save(a);
        });
        markConsumed(eventId, ConsumerEventType.FLIGHT_DELETED);
        log.info("Disabled FlightProjection: flightId={}", flightId);
    }

    @Override
    @Transactional
    public void upsertHotel(UUID eventId, ConsumerEventType eventType, HotelCatalogPayload payload) {
        if (alreadyConsumed(eventId, eventType)) return;

        UUID hotelId = payload.hotelId();
        HotelProjection hotel = hotelRoomTypeRepository.findById(hotelId).orElseGet(() -> {
            HotelProjection projection = new HotelProjection();
            projection.setId(hotelId);
            return projection;
        });

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

            upsertHotelRoomAvailability(roomTypeEvent.roomTypeId(), roomTypeEvent.totalRooms());
        }

        hotelRoomTypeRepository.save(hotel);
        markConsumed(eventId, eventType);
        log.info("Upserted HotelProjection: hotelId={}", hotelId);
    }

    @Override
    @Transactional
    public void disableHotel(UUID eventId, UUID hotelId) {
        if (alreadyConsumed(eventId, ConsumerEventType.HOTEL_DELETED)) return;

        hotelRoomTypeRepository.findById(hotelId).ifPresent(hotelProjection -> {
            hotelProjection.setStatus(ProjectionStatus.WITHDRAWN);
            hotelProjection.getRoomTypes().forEach(roomType ->
                    availabilityProjectionRepository.findByResourceTypeAndResourceId(ResourceType.HOTEL, roomType.getId())
                            .ifPresent(a -> {
                                a.setStatus(AvailabilityProjection.AvailabilityStatus.DISABLED);
                                availabilityProjectionRepository.save(a);
                            })
            );
            hotelRoomTypeRepository.save(hotelProjection);
        });
        markConsumed(eventId, ConsumerEventType.HOTEL_DELETED);
        log.info("Disabled HotelProjection: hotelId={}", hotelId);
    }

    @Override
    @Transactional
    public void incrementReserved(UUID eventId, ConsumerEventType eventType,
        ResourceType resourceType,
        UUID resourceId, int quantity) {
        if (alreadyConsumed(eventId, eventType)) {
            return;
        }

        availabilityProjectionRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .ifPresentOrElse(projection -> {
                projection.setReserved(projection.getReserved() + quantity);
                availabilityProjectionRepository.save(projection);
                log.info("Incremented reserved: resourceType={}, resourceId={}, qty={}",
                    resourceType, resourceId, quantity);
            }, () -> log.warn("AvailabilityProjection not found for increment: type={}, id={}",
                resourceType, resourceId));

        markConsumed(eventId, eventType);
    }

    @Override
    @Transactional
    public void decrementReserved(UUID eventId, ConsumerEventType eventType,
        ResourceType resourceType,
        UUID resourceId, int quantity) {
        if (alreadyConsumed(eventId, eventType)) {
            return;
        }

        availabilityProjectionRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .ifPresentOrElse(projection -> {
                projection.setReserved(Math.max(0, projection.getReserved() - quantity));
                availabilityProjectionRepository.save(projection);
                log.info("Decremented reserved: resourceType={}, resourceId={}, qty={}",
                    resourceType, resourceId, quantity);
            }, () -> log.warn("AvailabilityProjection not found for decrement: type={}, id={}",
                resourceType, resourceId));

        markConsumed(eventId, eventType);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertFlightAvailability(UUID flightId, int totalSeats) {
        AvailabilityProjection avail = availabilityProjectionRepository
                .findByResourceTypeAndResourceId(ResourceType.FLIGHT, flightId)
                .orElseGet(() -> {
                    AvailabilityProjection projection = new AvailabilityProjection();
                    projection.setId(UUID.randomUUID());
                    projection.setResourceType(ResourceType.FLIGHT);
                    projection.setResourceId(flightId);
                    projection.setReserved(0);
                    return projection;
                });
        avail.setCapacity(totalSeats);
        avail.setStatus(AvailabilityProjection.AvailabilityStatus.ACTIVE);
        availabilityProjectionRepository.save(avail);
    }

    private void upsertHotelRoomAvailability(UUID roomTypeId, int totalRooms) {
        AvailabilityProjection avail = availabilityProjectionRepository
                .findByResourceTypeAndResourceId(ResourceType.HOTEL, roomTypeId)
                .orElseGet(() -> {
                    AvailabilityProjection projection = new AvailabilityProjection();
                    projection.setId(UUID.randomUUID());
                    projection.setResourceType(ResourceType.HOTEL);
                    projection.setResourceId(roomTypeId);
                    projection.setReserved(0);
                    return projection;
                });
        avail.setCapacity(totalRooms);
        avail.setStatus(AvailabilityProjection.AvailabilityStatus.ACTIVE);
        availabilityProjectionRepository.save(avail);
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
