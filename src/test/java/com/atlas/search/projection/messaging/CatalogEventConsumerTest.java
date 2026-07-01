package com.atlas.search.projection.messaging;

import static org.mockito.Mockito.verify;

import com.atlas.search.projection.event.EventEnvelope;
import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.FlightDeletedPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.HotelDeletedPayload;
import com.atlas.search.projection.event.MoneyEvent;
import com.atlas.search.projection.service.ProjectionService;
import com.atlas.search.shared.messaging.ConsumerEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogEventConsumerTest {

  @Mock
  private ProjectionService projectionService;
  @Mock
  private EventValidator eventValidator;

  private CatalogEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CatalogEventConsumer(projectionService, eventValidator);
  }

  @Test
  void onFlightCreated_validatesEnvelopeAndDelegatesUpsert_withFlightCreatedType() {
    EventEnvelope<FlightCatalogPayload> envelope = flightEnvelope();

    consumer.onFlightCreated(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).upsertFlight(
        envelope.eventId(), ConsumerEventType.FLIGHT_CREATED, envelope.payload());
  }

  @Test
  void onFlightUpdated_validatesEnvelopeAndDelegatesUpsert_withFlightUpdatedType() {
    EventEnvelope<FlightCatalogPayload> envelope = flightEnvelope();

    consumer.onFlightUpdated(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).upsertFlight(
        envelope.eventId(), ConsumerEventType.FLIGHT_UPDATED, envelope.payload());
  }

  @Test
  void onFlightDeleted_validatesEnvelopeAndDelegatesDisable() {
    EventEnvelope<FlightDeletedPayload> envelope = new EventEnvelope<>(
        UUID.randomUUID(), "FlightDeleted", 1, Instant.now(), null, null, null, "flight-service",
        new FlightDeletedPayload(UUID.randomUUID()));

    consumer.onFlightDeleted(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).disableFlight(envelope.eventId(), envelope.payload().flightId());
  }

  @Test
  void onHotelCreated_validatesEnvelopeAndDelegatesUpsert_withHotelCreatedType() {
    EventEnvelope<HotelCatalogPayload> envelope = hotelEnvelope();

    consumer.onHotelCreated(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).upsertHotel(
        envelope.eventId(), ConsumerEventType.HOTEL_CREATED, envelope.payload());
  }

  @Test
  void onHotelUpdated_validatesEnvelopeAndDelegatesUpsert_withHotelUpdatedType() {
    EventEnvelope<HotelCatalogPayload> envelope = hotelEnvelope();

    consumer.onHotelUpdated(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).upsertHotel(
        envelope.eventId(), ConsumerEventType.HOTEL_UPDATED, envelope.payload());
  }

  @Test
  void onHotelDeleted_validatesEnvelopeAndDelegatesDisable() {
    EventEnvelope<HotelDeletedPayload> envelope = new EventEnvelope<>(
        UUID.randomUUID(), "HotelDeleted", 1, Instant.now(), null, null, null, "hotel-service",
        new HotelDeletedPayload(UUID.randomUUID()));

    consumer.onHotelDeleted(envelope);

    verify(eventValidator).validate(envelope);
    verify(projectionService).disableHotel(envelope.eventId(), envelope.payload().hotelId());
  }

  private EventEnvelope<FlightCatalogPayload> flightEnvelope() {
    FlightCatalogPayload payload = new FlightCatalogPayload(
        UUID.randomUUID(), "FL123", "DL", "Delta", "JFK", "LAX",
        Instant.parse("2026-07-10T10:00:00Z"), Instant.parse("2026-07-10T13:00:00Z"),
        150, new MoneyEvent(new BigDecimal("250.00"), "USD"), List.of());
    return new EventEnvelope<>(UUID.randomUUID(), "FlightCreated", 1, Instant.now(),
        null, null, null, "flight-service", payload);
  }

  private EventEnvelope<HotelCatalogPayload> hotelEnvelope() {
    HotelCatalogPayload payload = new HotelCatalogPayload(
        UUID.randomUUID(), "Grand Hotel", "Lima", "Peru", 4, List.of(), List.of(), List.of());
    return new EventEnvelope<>(UUID.randomUUID(), "HotelCreated", 1, Instant.now(),
        null, null, null, "hotel-service", payload);
  }
}
