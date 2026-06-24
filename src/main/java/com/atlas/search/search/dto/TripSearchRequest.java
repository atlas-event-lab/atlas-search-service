package com.atlas.search.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Query parameters for {@code GET /search/trips} (contracts/openapi/search.yaml).
 * Bean Validation covers field-level constraints; cross-field rules are enforced in the service.
 */
@Getter
@Setter
public class TripSearchRequest {

    // ── Required ──────────────────────────────────────────────────────────────

    @NotBlank
    private String origin;

    @NotBlank
    private String destination;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departureDate;

    @NotNull
    @Min(1) @Max(9)
    private Integer adults;

    // ── Optional trip shape ───────────────────────────────────────────────────

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate returnDate;

    @Min(0)
    private int children = 0;

    @Min(0)
    private int infants = 0;

    @Min(1)
    private int rooms = 1;

    /** Accepted per contract but not used for filtering until dep. A3 is resolved (Flight catalog). */
    private CabinClass cabinClass;

    // ── Optional filters ──────────────────────────────────────────────────────

    /**
     * When true, restrict to non-stop flights. Inert until dep. A1 is resolved
     * (Flight Service must publish segment count in FlightCreated/Updated).
     */
    private Boolean nonStop;

    /**
     * Maximum stops allowed. Inert until dep. A1 is resolved.
     */
    @Min(0)
    private Integer stops;

    private List<String> airlines;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    @Min(1) @Max(5)
    private Integer hotelRating;

    // ── Sort & paging ─────────────────────────────────────────────────────────

    private SortOption sort = SortOption.PRICE;

    @Min(0)
    private int page = 0;

    @Min(1) @Max(100)
    private int size = 20;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum CabinClass {
        ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST
    }

    public enum SortOption {
        PRICE, DEPARTURE_TIME, DURATION
    }
}
