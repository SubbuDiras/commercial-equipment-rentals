package com.marketplace.booking;

import java.time.Instant;

public record OwnerBookingSummary(
        String bookingId,
        String bookingReference,
        String equipmentId,
        String equipmentTitle,
        String renterName,
        Instant startTime,
        Instant endTime,
        String deliveryOption,
        String status,
        Instant holdExpiresAt,
        Instant requestedAt,
        BookingPricing pricing
) {
    OwnerBookingSummary(BookingResponse booking, String equipmentTitle, String renterName, Instant requestedAt) {
        this(booking.bookingId(), booking.bookingReference(), booking.equipmentId(), equipmentTitle, renterName,
                booking.startTime(), booking.endTime(), booking.deliveryOption(), booking.status(),
                booking.holdExpiresAt(), requestedAt, booking.pricing());
    }
}
