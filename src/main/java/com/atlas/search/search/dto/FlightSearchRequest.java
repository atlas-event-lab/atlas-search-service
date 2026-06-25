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
 * Query parameters for {@code GET /search/flights} (search.yaml).
 * Bean Validation covers field-level constraints; cross-field rules are enforced in the service.
 */
@Getter
@Setter
public class FlightSearchRequest {

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

    @Min(0)
    private int children = 0;

    @Min(0)
    private int infants = 0;

    private CabinClass cabinClass;

    private Boolean nonStop;

    @Min(0)
    private Integer stops;

    private List<String> airlines;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private FlightSortOption sort = FlightSortOption.PRICE;

    @Min(0)
    private int page = 0;

    @Min(1) @Max(100)
    private int size = 20;

    public enum CabinClass {
        ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST
    }

    public enum FlightSortOption {
        PRICE, DEPARTURE_TIME, DURATION
    }
}
