package com.marketplace.booking;

import java.time.Instant;

public record BookingResponse(
        String bookingId,
        String bookingReference,
        String equipmentId,
        Instant startTime,
        Instant endTime,
        String deliveryOption,
        String status,
        Instant holdExpiresAt,
        BookingPricing pricing
) {
}
