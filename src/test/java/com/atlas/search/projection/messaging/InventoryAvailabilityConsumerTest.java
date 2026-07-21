package com.atlas.search.projection.messaging;

import static org.mockito.Mockito.verify;

import com.atlas.search.projection.event.EventEnvelope;
import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.FlightAvailabilityPayload;
import com.atlas.search.projection.event.HotelAvailabilityPayload;
import com.atlas.search.projection.event.NightAvailability;
import com.atlas.search.projection.service.ProjectionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryAvailabilityConsumerTest {

    @Mock
    private ProjectionService projectionService;

    @Mock
    private EventValidator eventValidator;

    private InventoryAvailabilityConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryAvailabilityConsumer(projectionService, eventValidator);
    }

    @Test
    void onFlightReserved_validatesAndAppliesAbsoluteFlightAvailability() {
        FlightAvailabilityPayload payload =
                new FlightAvailabilityPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, 173L);
        EventEnvelope<FlightAvailabilityPayload> envelope = flightEnvelope(payload);

        consumer.onFlightReserved(envelope);

        verify(eventValidator).validate(envelope);
        verify(projectionService).applyFlightAvailability(payload);
    }

    @Test
    void onFlightReleased_appliesAbsoluteFlightAvailability() {
        FlightAvailabilityPayload payload =
                new FlightAvailabilityPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, 200L);
        EventEnvelope<FlightAvailabilityPayload> envelope = flightEnvelope(payload);

        consumer.onFlightReleased(envelope);

        verify(projectionService).applyFlightAvailability(payload);
    }

    @Test
    void onFlightExpired_appliesAbsoluteFlightAvailability() {
        FlightAvailabilityPayload payload =
                new FlightAvailabilityPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 300L);
        EventEnvelope<FlightAvailabilityPayload> envelope = flightEnvelope(payload);

        consumer.onFlightExpired(envelope);

        verify(projectionService).applyFlightAvailability(payload);
    }

    @Test
    void onHotelReserved_validatesAndAppliesPerNightAbsoluteAvailability() {
        HotelAvailabilityPayload payload = hotelPayload(4, 100L);
        EventEnvelope<HotelAvailabilityPayload> envelope = hotelEnvelope(payload);

        consumer.onHotelReserved(envelope);

        verify(eventValidator).validate(envelope);
        verify(projectionService).applyHotelAvailability(payload);
    }

    @Test
    void onHotelReleased_appliesPerNightAbsoluteAvailability() {
        HotelAvailabilityPayload payload = hotelPayload(1, 150L);
        EventEnvelope<HotelAvailabilityPayload> envelope = hotelEnvelope(payload);

        consumer.onHotelReleased(envelope);

        verify(projectionService).applyHotelAvailability(payload);
    }

    @Test
    void onHotelExpired_appliesPerNightAbsoluteAvailability() {
        HotelAvailabilityPayload payload = hotelPayload(0, 200L);
        EventEnvelope<HotelAvailabilityPayload> envelope = hotelEnvelope(payload);

        consumer.onHotelExpired(envelope);

        verify(projectionService).applyHotelAvailability(payload);
    }

    private HotelAvailabilityPayload hotelPayload(int reserved, long version) {
        return new HotelAvailabilityPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new NightAvailability(LocalDate.of(2026, 8, 1), reserved)),
                version);
    }

    private EventEnvelope<FlightAvailabilityPayload> flightEnvelope(FlightAvailabilityPayload payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                "FLIGHT_SEATS_RESERVED",
                1,
                Instant.now(),
                null,
                null,
                null,
                "inventory-service",
                payload);
    }

    private EventEnvelope<HotelAvailabilityPayload> hotelEnvelope(HotelAvailabilityPayload payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                "HOTEL_ROOMS_RESERVED",
                1,
                Instant.now(),
                null,
                null,
                null,
                "inventory-service",
                payload);
    }
}
