package com.atlas.search.projection.dto;

import jakarta.validation.constraints.NotBlank;

public record ImageDto(@NotBlank String url, String caption) {}
