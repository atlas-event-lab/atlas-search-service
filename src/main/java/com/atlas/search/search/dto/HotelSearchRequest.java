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

/**
 * Query parameters for {@code GET /search/hotels} (search.yaml).
 * Bean Validation covers field-level constraints; cross-field rules are enforced in the service.
 */
@Getter
@Setter
public class HotelSearchRequest {

    @NotBlank
    private String city;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut;

    @NotNull
    @Min(1)
    private Integer rooms = 1;

    @Min(1)
    private Integer guests;

    @Min(1) @Max(5)
    private Integer hotelRating;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private HotelSortOption sort = HotelSortOption.PRICE;

    @Min(0)
    private int page = 0;

    @Min(1) @Max(100)
    private int size = 20;

    public enum HotelSortOption {
        PRICE, RATING
    }
}
