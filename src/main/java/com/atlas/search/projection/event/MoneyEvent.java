package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Money representation inside catalog event payloads (copied from flight/hotel-events.yaml Money). */
public record MoneyEvent(@NotNull BigDecimal amount, String currency) {}
