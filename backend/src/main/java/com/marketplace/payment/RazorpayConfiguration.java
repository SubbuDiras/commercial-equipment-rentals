package com.marketplace.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({RazorpayProperties.class, PaymentOperationsProperties.class})
public class RazorpayConfiguration {

    @Bean
    public RestClient razorpayRestClient(RestClient.Builder builder, RazorpayProperties properties) {
        return builder
                .baseUrl(properties.apiBaseUrl() == null || properties.apiBaseUrl().isBlank()
                        ? "https://api.razorpay.com/v1"
                        : properties.apiBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.keyId(), properties.keySecret()))
                .build();
    }
}
