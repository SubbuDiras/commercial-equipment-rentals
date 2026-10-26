package com.marketplace.payment;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/bookings/{bookingId}/payment-order")
    @PreAuthorize("hasRole('RENTER')")
    public PaymentOrderResponse createOrder(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.createOrder(bookingId, jwt);
    }

    @PostMapping("/api/v1/payments/{paymentId}/verify")
    @PreAuthorize("hasRole('RENTER')")
    public PaymentResponse verify(
            @PathVariable String paymentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PaymentVerificationRequest request
    ) {
        return paymentService.verify(paymentId, request, jwt);
    }
}
