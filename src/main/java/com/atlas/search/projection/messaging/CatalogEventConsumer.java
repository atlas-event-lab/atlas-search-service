package com.atlas.search.projection.messaging;

import com.atlas.search.projection.event.EventEnvelope;
import com.atlas.search.projection.event.EventValidator;
import com.atlas.search.projection.event.FlightCatalogPayload;
import com.atlas.search.projection.event.FlightDeletedPayload;
import com.atlas.search.projection.event.HotelCatalogPayload;
import com.atlas.search.projection.event.HotelDeletedPayload;
import com.atlas.search.projection.service.ProjectionService;
import com.atlas.search.shared.messaging.ConsumerEventType;
import com.atlas.search.shared.messaging.EventTopics;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes Flight and Hotel catalog events to maintain FlightProjection, HotelProjection and the
 * initial AvailabilityProjection seeding. Delegates all business logic to
 * {@link ProjectionService}. Idempotent on envelope {@code eventId} (EVT-005, EVT-008). Retry
 * strategy: 4 attempts with exponential back-off (5s → 30s → 120s → DLQ). Malformed envelopes
 * ({@link IllegalArgumentException} / {@link ConstraintViolationException}) are non-retryable → DLQ
 * immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private static final long RETRY_DELAY_MS = 5_000L;
    private static final double RETRY_MULTIPLIER = 6.0;
    private static final long RETRY_MAX_DELAY_MS = 120_000L;

    private final ProjectionService projectionService;
    private final EventValidator eventValidator;

    // ── Flight ────────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.FLIGHT_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onFlightCreated(EventEnvelope<FlightCatalogPayload> envelope) {
        processFlightUpsert(envelope, ConsumerEventType.FLIGHT_CREATED);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.FLIGHT_UPDATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onFlightUpdated(EventEnvelope<FlightCatalogPayload> envelope) {
        processFlightUpsert(envelope, ConsumerEventType.FLIGHT_UPDATED);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.FLIGHT_DELETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onFlightDeleted(EventEnvelope<FlightDeletedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId = envelope.eventId();
        UUID flightId = envelope.payload().flightId();
        log.info("Received FlightDeleted: eventId={}, flightId={}", eventId, flightId);
        projectionService.disableFlight(eventId, flightId);
    }

    // ── Hotel ─────────────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.HOTEL_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onHotelCreated(EventEnvelope<HotelCatalogPayload> envelope) {
        processHotelUpsert(envelope, ConsumerEventType.HOTEL_CREATED);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.HOTEL_UPDATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onHotelUpdated(EventEnvelope<HotelCatalogPayload> envelope) {
        processHotelUpsert(envelope, ConsumerEventType.HOTEL_UPDATED);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {IllegalArgumentException.class, ConstraintViolationException.class})
    @KafkaListener(topics = EventTopics.HOTEL_DELETED, groupId = "${spring.kafka.consumer.group-id}")
    public void onHotelDeleted(EventEnvelope<HotelDeletedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId = envelope.eventId();
        UUID hotelId = envelope.payload().hotelId();
        log.info("Received HotelDeleted: eventId={}, hotelId={}", eventId, hotelId);
        projectionService.disableHotel(eventId, hotelId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void processFlightUpsert(EventEnvelope<FlightCatalogPayload> envelope, ConsumerEventType eventType) {
        eventValidator.validate(envelope);
        UUID eventId = envelope.eventId();
        log.info("Received {}: eventId={}", eventType, eventId);
        projectionService.upsertFlight(eventId, eventType, envelope.payload());
    }

    private void processHotelUpsert(EventEnvelope<HotelCatalogPayload> envelope, ConsumerEventType eventType) {
        eventValidator.validate(envelope);
        UUID eventId = envelope.eventId();
        log.info("Received {}: eventId={}", eventType, eventId);
        projectionService.upsertHotel(eventId, eventType, envelope.payload());
    }
}
