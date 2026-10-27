package com.marketplace.booking;

import com.marketplace.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/owner/bookings")
@PreAuthorize("hasRole('OWNER')")
@Validated
public class OwnerBookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;

    public OwnerBookingController(BookingService bookingService, CancellationService cancellationService) {
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
    }

    @PostMapping("/{bookingId}/cancel")
    public CancellationResponse cancel(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CancellationRequest request
    ) {
        return cancellationService.cancelByOwner(bookingId, jwt, request.reason());
    }

    @GetMapping
    public PageResponse<OwnerBookingSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<BookingStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return bookingService.listForOwner(jwt, status, page, size);
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
