package com.marketplace.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingCreateRequest(
        @NotBlank String equipmentId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull DeliveryOption deliveryOption,
        @Valid DeliveryAddressRequest deliveryAddress
) {
}
