package com.marketplace.review;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param windowDays days after a booking is {@code COMPLETED} during which either side may review; the booking
 *                   is then {@code CLOSED} even if a side has not reviewed
 */
@ConfigurationProperties(prefix = "reviews")
public record ReviewProperties(@DefaultValue("14") int windowDays) {
}
