package com.rental.Marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.PaymentOperationsDisabledException;
import com.marketplace.payment.PaymentOperationsProperties;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.RazorpayProperties;
import com.marketplace.payment.RazorpaySignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOperationsGateTests {

    @Test
    void financialOperationsAreDisabledByDefault() {
        PaymentService paymentService = paymentService(
                new PaymentOperationsProperties(false, false, true),
                new RazorpayProperties("rzp_test_key", "test-secret", "webhook-secret", null),
                new StandardEnvironment());

        assertThat(paymentService.financialOperationsEnabled()).isFalse();
        assertThatThrownBy(() -> paymentService.releaseMaturedSettlements())
                .isInstanceOf(PaymentOperationsDisabledException.class);
    }

    @Test
    void nonProductionOperationsRejectLiveCredentialsEvenWhenEnabled() {
        PaymentService paymentService = paymentService(
                new PaymentOperationsProperties(true, false, true),
                new RazorpayProperties("rzp_live_key", "live-secret", "webhook-secret", null),
                new StandardEnvironment());

        assertThat(paymentService.financialOperationsEnabled()).isFalse();
        assertThatThrownBy(() -> paymentService.releaseMaturedSettlements())
                .isInstanceOf(PaymentOperationsDisabledException.class);
    }

    @Test
    void productionOperationsRequireExplicitApprovalAndLiveMode() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        PaymentService paymentService = paymentService(
                new PaymentOperationsProperties(true, false, true),
                new RazorpayProperties("rzp_live_key", "live-secret", "webhook-secret", null),
                environment);

        assertThatThrownBy(() -> paymentService.releaseMaturedSettlements())
                .isInstanceOf(PaymentOperationsDisabledException.class);
    }

    private PaymentService paymentService(
            PaymentOperationsProperties operations,
            RazorpayProperties razorpay,
            StandardEnvironment environment
    ) {
        return new PaymentService(
                null,
                new ObjectMapper(),
                null,
                new RazorpaySignatureVerifier(),
                razorpay,
                operations,
                environment);
    }
}
