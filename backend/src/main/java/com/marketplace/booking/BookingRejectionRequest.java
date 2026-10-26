package com.marketplace.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingRejectionRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
