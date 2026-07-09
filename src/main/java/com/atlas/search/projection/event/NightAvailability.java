package com.atlas.search.projection.event;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * One night's new <b>absolute</b> reserved count inside a {@link HotelAvailabilityPayload}
 * (inventory-events.yaml NightAvailability, ADR-0008). Copied from inventory-service.
 */
public record NightAvailability(
        @NotNull
        LocalDate stayDate,
        int reserved
) {}
