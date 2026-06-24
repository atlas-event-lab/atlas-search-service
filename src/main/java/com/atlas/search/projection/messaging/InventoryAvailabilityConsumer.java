package com.atlas.search.projection.messaging;

import com.atlas.search.projection.entity.ResourceType;
import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.ReservationDeltaPayload;
import com.atlas.search.projection.service.ProjectionService;
import com.atlas.search.shared.messaging.ConsumerEventType;
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

import java.util.UUID;

/**
 * Consumes Inventory resource-facing events to adjust the AvailabilityProjection. Search consumes
 * ONLY the resource-facing family (keyed by reservationId), never the booking-facing saga family
 * (inventory/service.md). Adjustments are commutative counters — safe under at-least-once delivery
 * when deduplicated on {@code eventId} (events.md §Processing rules).
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

  // ── Flight availability ───────────────────────────────────────────────────

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq",
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightReserved(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_FLIGHT_RESERVED,
        ResourceType.FLIGHT, true);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq",
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_RELEASED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightReleased(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_FLIGHT_RELEASED,
        ResourceType.FLIGHT, false);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq",
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_FLIGHT_EXPIRED, groupId = "${spring.kafka.consumer.group-id}")
  public void onFlightExpired(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_FLIGHT_EXPIRED,
        ResourceType.FLIGHT, false);
  }

  // ── Hotel availability ────────────────────────────────────────────────────

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq", dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelReserved(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_HOTEL_RESERVED,
        ResourceType.HOTEL, true);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq",
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_RELEASED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelReleased(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_HOTEL_RELEASED,
        ResourceType.HOTEL, false);
  }

  @RetryableTopic(attempts = "4",
      backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
      dltTopicSuffix = ".dlq",
      dltStrategy = DltStrategy.FAIL_ON_ERROR,
      autoStartDltHandler = "false",
      exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
  @KafkaListener(topics = EventTopics.INVENTORY_HOTEL_EXPIRED, groupId = "${spring.kafka.consumer.group-id}")
  public void onHotelExpired(EventEnvelope<ReservationDeltaPayload> envelope) {
    processAvailabilityChange(envelope, ConsumerEventType.INVENTORY_HOTEL_EXPIRED,
        ResourceType.HOTEL, false);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void processAvailabilityChange(EventEnvelope<ReservationDeltaPayload> envelope,
      ConsumerEventType eventType, ResourceType resourceType, boolean increment) {
    eventValidator.validate(envelope);
    UUID eventId = envelope.eventId();
    ReservationDeltaPayload payload = envelope.payload();
    UUID resourceId = payload.resourceId();
    int quantity = payload.quantity();

    log.debug("Received {}: eventId={}, resourceType={}, resourceId={}, qty={}",
        eventType, eventId, resourceType, resourceId, quantity);

    if (increment) {
      projectionService.incrementReserved(eventId, eventType, resourceType, resourceId, quantity);
    } else {
      projectionService.decrementReserved(eventId, eventType, resourceType, resourceId, quantity);
    }
  }
}
