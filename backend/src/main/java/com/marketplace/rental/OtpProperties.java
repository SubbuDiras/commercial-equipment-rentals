package com.marketplace.rental;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.otp")
public record OtpProperties(String secret) {
}
