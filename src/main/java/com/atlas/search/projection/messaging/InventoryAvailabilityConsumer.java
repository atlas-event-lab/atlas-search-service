package com.atlas.search.projection.messaging;

import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.FlightAvailabilityPayload;
import com.atlas.search.projection.event.HotelAvailabilityPayload;
import com.atlas.search.projection.service.ProjectionService;
import com.atlas.search.projection.event.EventEnvelope;
import com.atlas.search.shared.messaging.EventTopics;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes Inventory resource-facing events to update the availability projections (ADR-0008/ADR-0009).
 * Search consumes ONLY the resource-facing family (rekeyed by flightId / roomTypeId), never the
 * booking-facing saga family. Events carry the <b>absolute</b> {@code reserved} value + a monotonic
 * {@code version}; the consumer applies an update last-writer-wins, only if {@code version ≥} the
 * stored version. This is idempotent under at-least-once delivery without eventId dedupe.
 * The reserved / released / expired events of a family share a payload and are applied identically
 * (the absolute value already reflects the outcome).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAvailabilityConsumer {

  private static final long RETRY_DELAY_MS = 5_000L;
  private static final double RETRY_MULTIPLIER = 6.0;
  private static final long RETRY_MAX_DELAY_MS = 120_000L;

  private final ProjectionService projectionService;
  private final EventValidator eventValidator;

  // ── Flight availability (absolute, keyed by flightId) ─────────────────────

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightReserved(EventEnvelope<FlightAvailabilityPayload> envelope) {
    applyFlight(envelope);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_RELEASED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightReleased(EventEnvelope<FlightAvailabilityPayload> envelope) {
    applyFlight(envelope);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_EXPIRED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightExpired(EventEnvelope<FlightAvailabilityPayload> envelope) {
    applyFlight(envelope);
  }

  // ── Hotel availability (per-night absolute, keyed by roomTypeId) ──────────

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelReserved(EventEnvelope<HotelAvailabilityPayload> envelope) {
    applyHotel(envelope);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_RELEASED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelReleased(EventEnvelope<HotelAvailabilityPayload> envelope) {
    applyHotel(envelope);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR, autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_EXPIRED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelExpired(EventEnvelope<HotelAvailabilityPayload> envelope) {
    applyHotel(envelope);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void applyFlight(EventEnvelope<FlightAvailabilityPayload> envelope) {
    eventValidator.validate(envelope);
    FlightAvailabilityPayload payload = envelope.payload();
    log.info("Received {}: flightId={}, reserved={}, version={}",
        envelope.eventType(), payload.resourceId(), payload.reserved(), payload.version());
    projectionService.applyFlightAvailability(payload);
  }

  private void applyHotel(EventEnvelope<HotelAvailabilityPayload> envelope) {
    eventValidator.validate(envelope);
    HotelAvailabilityPayload payload = envelope.payload();
    log.info("Received {}: roomTypeId={}, nights={}, version={}",
        envelope.eventType(), payload.roomTypeId(), payload.nights().size(), payload.version());
    projectionService.applyHotelAvailability(payload);
  }
}
