package com.atlas.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the per-night hotel projection and query (ADR-0008/ADR-0009;
 * no hardcoded values, coding-standards §Configuration).
 *
 * @param horizonDays    how far ahead hotels are bookable. Catalog events materialize the nights
 *                       {@code [today, today + horizonDays)}; the rolling job keeps the window this
 *                       size (must match Inventory's horizon; recommended 365).
 * @param purgeAfterDays how long a completed night is kept before the rolling job purges it: nights
 *                       with {@code stayDate < today − purgeAfterDays} are deleted (recommended 7).
 * @param maxStayNights  the maximum {@code nights = checkOut − checkIn} accepted by the hotel query;
 *                       longer stays are rejected 400 (recommended 30).
 */
@ConfigurationProperties(prefix = "atlas.search.hotel")
public record HotelSearchProperties(int horizonDays, int purgeAfterDays, int maxStayNights) {}
