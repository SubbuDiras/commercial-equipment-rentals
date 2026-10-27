package com.marketplace.booking;

import com.marketplace.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private final BookingService bookingService;
    private final BookingDetailsService bookingDetailsService;
    private final CancellationService cancellationService;

    public BookingController(
            BookingService bookingService,
            BookingDetailsService bookingDetailsService,
            CancellationService cancellationService
    ) {
        this.bookingService = bookingService;
        this.bookingDetailsService = bookingDetailsService;
        this.cancellationService = cancellationService;
    }

    @GetMapping("/{bookingId}/cancellation-quote")
    @PreAuthorize("hasAnyRole('RENTER', 'OWNER')")
    public CancellationResponse cancellationQuote(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return cancellationService.quote(bookingId, jwt);
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('RENTER')")
    public CancellationResponse cancel(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CancellationRequest request
    ) {
        return cancellationService.cancelByRenter(bookingId, jwt, request.reason());
    }

    @PostMapping
    @PreAuthorize("hasRole('RENTER')")
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BookingCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(jwt, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('RENTER')")
    public PageResponse<RenterBookingSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return bookingService.listForRenter(jwt, page, size);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('RENTER', 'OWNER', 'ADMIN')")
    public BookingDetailsResponse get(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return bookingDetailsService.get(bookingId, jwt, authentication.getAuthorities());
    }
}
