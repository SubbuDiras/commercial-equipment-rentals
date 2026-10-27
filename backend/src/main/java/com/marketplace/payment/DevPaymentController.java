package com.marketplace.payment;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exists only while the fake Razorpay gateway is active (dev/test). */
@RestController
@RequestMapping("/api/v1/dev/payments")
@ConditionalOnProperty(name = "payment.razorpay.fake", havingValue = "true")
@PreAuthorize("hasRole('RENTER')")
public class DevPaymentController {

    private final FakeCheckoutService fakeCheckoutService;

    public DevPaymentController(FakeCheckoutService fakeCheckoutService) {
        this.fakeCheckoutService = fakeCheckoutService;
    }

    @PostMapping("/{orderId}/simulate")
    public SimulatedCheckoutResponse simulate(
            @PathVariable String orderId,
            @Valid @RequestBody SimulatePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return fakeCheckoutService.simulate(orderId, request.outcome(), jwt);
    }
}
