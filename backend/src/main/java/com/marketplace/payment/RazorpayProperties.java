package com.marketplace.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param fake use the in-process {@link FakeRazorpayGateway} instead of calling Razorpay (dev/test profiles only)
 */
@ConfigurationProperties(prefix = "payment.razorpay")
public record RazorpayProperties(
        String keyId,
        String keySecret,
        String webhookSecret,
        String apiBaseUrl,
        boolean fake
) {
}
