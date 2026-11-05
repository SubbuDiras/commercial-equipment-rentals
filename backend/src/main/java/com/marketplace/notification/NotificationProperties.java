package com.marketplace.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled    send emails; off where no SMTP server is available (tests, some environments)
 * @param from       sender address
 * @param appBaseUrl frontend base URL used for links in emails
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("noreply@marketplace.local") String from,
        @DefaultValue("http://localhost:4200") String appBaseUrl
) {
}
