package com.marketplace.booking;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
public class OwnerBookingController {

    private final BookingService bookingService;

    public OwnerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PatchMapping("/{bookingId}/accept")
    public BookingResponse accept(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return bookingService.accept(bookingId, jwt);
    }

    @PatchMapping("/{bookingId}/reject")
    public BookingResponse reject(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingRejectionRequest request
    ) {
        return bookingService.reject(bookingId, jwt, request.reason());
    }
}
