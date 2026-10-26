package com.marketplace.rental;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OtpProperties.class)
public class OtpConfiguration {
}
