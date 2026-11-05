package com.marketplace.booking;

import java.time.Instant;

/** A row in the renter's booking history: the booking plus the equipment title. */
public record RenterBookingSummary(
        String bookingId,
        String bookingReference,
        String equipmentId,
        String equipmentTitle,
        Instant startTime,
        Instant endTime,
        String deliveryOption,
        String status,
        Instant holdExpiresAt,
        BookingPricing pricing
) {
    RenterBookingSummary(BookingResponse booking, String equipmentTitle) {
        this(booking.bookingId(), booking.bookingReference(), booking.equipmentId(), equipmentTitle,
                booking.startTime(), booking.endTime(), booking.deliveryOption(), booking.status(),
                booking.holdExpiresAt(), booking.pricing());
    }
}
