package com.marketplace.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DamageClaimRequest(
        @NotBlank @Size(max = 50) String category,
        @NotNull @PositiveOrZero BigDecimal claimAmount,
        @NotBlank @Size(max = 2000) String description
) {
}
