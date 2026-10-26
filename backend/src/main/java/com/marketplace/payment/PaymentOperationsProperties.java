package com.marketplace.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.operations")
public record PaymentOperationsProperties(
        boolean enabled,
        boolean productionApproved,
        boolean sandboxOnly
) {
}
