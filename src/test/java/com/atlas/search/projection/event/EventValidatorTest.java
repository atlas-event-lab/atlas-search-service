package com.atlas.search.projection.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventValidatorTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final EventValidator eventValidator = new EventValidator(validator);

  @Test
  void validate_doesNotThrow_whenEnvelopeIsValid() {
    EventEnvelope<FlightCatalogPayload> envelope = new EventEnvelope<>(
        UUID.randomUUID(), "FlightCreated", 1, Instant.now(), null, null, null, "flight-service",
        new FlightCatalogPayload(UUID.randomUUID(), "FL123", "DL", "Delta", "JFK", "LAX",
            Instant.now(), Instant.now(), 150,
            new MoneyEvent(new BigDecimal("100.00"), "USD"), List.of()));

    assertThatCode(() -> eventValidator.validate(envelope)).doesNotThrowAnyException();
  }

  @Test
  void validate_throwsConstraintViolationException_whenEventIdIsNull() {
    EventEnvelope<FlightCatalogPayload> envelope = new EventEnvelope<>(
        null, "FlightCreated", 1, Instant.now(), null, null, null, "flight-service",
        new FlightCatalogPayload(UUID.randomUUID(), "FL123", "DL", "Delta", "JFK", "LAX",
            Instant.now(), Instant.now(), 150,
            new MoneyEvent(new BigDecimal("100.00"), "USD"), List.of()));

    assertThatThrownBy(() -> eventValidator.validate(envelope))
        .isInstanceOf(ConstraintViolationException.class)
        .satisfies(ex -> assertThat(((ConstraintViolationException) ex).getConstraintViolations())
            .isNotEmpty());
  }

  @Test
  void validate_throwsConstraintViolationException_whenNestedPayloadIsInvalid() {
    EventEnvelope<FlightCatalogPayload> envelope = new EventEnvelope<>(
        UUID.randomUUID(), "FlightCreated", 1, Instant.now(), null, null, null, "flight-service",
        new FlightCatalogPayload(null, "FL123", "DL", "Delta", "JFK", "LAX",
            Instant.now(), Instant.now(), 150,
            new MoneyEvent(new BigDecimal("100.00"), "USD"), List.of()));

    assertThatThrownBy(() -> eventValidator.validate(envelope))
        .isInstanceOf(ConstraintViolationException.class);
  }
}
