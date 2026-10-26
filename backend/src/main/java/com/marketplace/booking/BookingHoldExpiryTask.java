package com.marketplace.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingHoldExpiryTask {

    private final BookingService bookingService;

    public BookingHoldExpiryTask(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.sweep-interval-ms:60000}")
    public void expireHolds() {
        bookingService.expireHolds();
    }
}
