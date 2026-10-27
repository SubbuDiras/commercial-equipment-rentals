package com.marketplace.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Draft cancellation policy (unresolved-business-decisions.md, Decision 6), pending legal review.
 *
 * @param fullRefundHours                 at least this many hours before start: full rental refund less the gateway fee
 * @param partialRefundHours              at least this many hours (and under {@code fullRefundHours}): partial refund
 * @param partialRefundPercent            share of rental charges refunded in the partial tier
 * @param gatewayFeePercent               share of rental charges withheld in the full-refund tier
 * @param partialOwnerCompensationPercent share of the owner's expected payout paid in the partial tier
 * @param lateOwnerCompensationPercent    share of the owner's expected payout paid when under {@code partialRefundHours}
 * @param ownerCancellationPlatformCredit credit promised to the renter when the owner cancels a paid booking
 */
@ConfigurationProperties(prefix = "cancellation")
public record CancellationProperties(
        @DefaultValue("48") int fullRefundHours,
        @DefaultValue("24") int partialRefundHours,
        @DefaultValue("50") BigDecimal partialRefundPercent,
        @DefaultValue("2") BigDecimal gatewayFeePercent,
        @DefaultValue("0") BigDecimal partialOwnerCompensationPercent,
        @DefaultValue("80") BigDecimal lateOwnerCompensationPercent,
        @DefaultValue("500.00") BigDecimal ownerCancellationPlatformCredit
) {
}
