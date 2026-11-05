package com.marketplace.booking;

import java.math.BigDecimal;

/**
 * What a cancellation does with the money. {@code totalRefund = rentalRefund + depositRefund}; the gateway fee and
 * any rental charges not refunded stay with the platform, from which {@code ownerCompensation} is paid out.
 * {@code platformCredit} is recorded only; no credit ledger exists yet.
 */
public record CancellationBreakdown(
        String cancelledBy,
        String policyTier,
        BigDecimal hoursBeforeStart,
        String currency,
        BigDecimal amountPaid,
        BigDecimal rentalCharges,
        BigDecimal rentalRefund,
        BigDecimal gatewayFee,
        BigDecimal depositRefund,
        BigDecimal totalRefund,
        BigDecimal ownerCompensation,
        BigDecimal platformCredit,
        String policyNotice
) {
}
