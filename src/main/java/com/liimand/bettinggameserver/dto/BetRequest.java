package com.liimand.bettinggameserver.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BetRequest(
        @NotBlank String nickname,
        @Min(1) @Max(10) int number,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
