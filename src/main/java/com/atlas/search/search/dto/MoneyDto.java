package com.atlas.search.search.dto;

import java.math.BigDecimal;

/** Monetary value DTO (contracts/openapi/common/money.yaml, domain/money.md). */
public record MoneyDto(BigDecimal amount, String currency) {}
