package com.marketplace.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DisputeResolutionRequest(
        @NotNull @PositiveOrZero BigDecimal approvedDeductionAmount,
        @NotBlank @Size(max = 2000) String resolutionNotes
) {
}
