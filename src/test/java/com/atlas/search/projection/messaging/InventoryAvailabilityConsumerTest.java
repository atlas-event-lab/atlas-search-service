package com.atlas.search.projection.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.event.EventEnvelope;
import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.ReservationDeltaPayload;
import com.atlas.search.projection.service.ProjectionService;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.time.Instant;
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
  void onFlightReserved_validatesEnvelopeAndIncrementsFlightReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onFlightReserved(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).incrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_FLIGHT_RESERVED, ResourceType.FLIGHT,
        envelope.payload().resourceId(), envelope.payload().quantity());
    verify(projectionService, never()).decrementReserved(any(), any(), any(), any(), anyInt());
  }

  @Test
  void onFlightReleased_validatesEnvelopeAndDecrementsFlightReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onFlightReleased(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).decrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_FLIGHT_RELEASED, ResourceType.FLIGHT,
        envelope.payload().resourceId(), envelope.payload().quantity());
  }

  @Test
  void onFlightExpired_validatesEnvelopeAndDecrementsFlightReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onFlightExpired(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).decrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_FLIGHT_EXPIRED, ResourceType.FLIGHT,
        envelope.payload().resourceId(), envelope.payload().quantity());
  }

  @Test
  void onHotelReserved_validatesEnvelopeAndIncrementsHotelReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onHotelReserved(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).incrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_HOTEL_RESERVED, ResourceType.HOTEL,
        envelope.payload().resourceId(), envelope.payload().quantity());
  }

  @Test
  void onHotelReleased_validatesEnvelopeAndDecrementsHotelReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onHotelReleased(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).decrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_HOTEL_RELEASED, ResourceType.HOTEL,
        envelope.payload().resourceId(), envelope.payload().quantity());
  }

  @Test
  void onHotelExpired_validatesEnvelopeAndDecrementsHotelReserved() {
    EventEnvelope<ReservationDeltaPayload> envelope = envelope();

    consumer.onHotelExpired(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).decrementReserved(envelope.eventId(),
        ConsumerEventType.INVENTORY_HOTEL_EXPIRED, ResourceType.HOTEL,
        envelope.payload().resourceId(), envelope.payload().quantity());
  }

  private EventEnvelope<ReservationDeltaPayload> envelope() {
    ReservationDeltaPayload payload = new ReservationDeltaPayload(
        UUID.randomUUID(), UUID.randomUUID(), "FLIGHT", UUID.randomUUID(), 2);
    return new EventEnvelope<>(UUID.randomUUID(), "InventoryFlightReserved", 1, Instant.now(),
        null, null, null, "inventory-service", payload);
  }
}
