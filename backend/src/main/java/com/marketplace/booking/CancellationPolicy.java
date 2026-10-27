package com.marketplace.booking;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Calculates refunds for the draft cancellation policy. Pure arithmetic: it moves no money and reads no data.
 */
@Component
@EnableConfigurationProperties(CancellationProperties.class)
public class CancellationPolicy {

    public static final String NO_PAYMENT = "NO_PAYMENT";
    public static final String FULL_REFUND = "FULL_REFUND";
    public static final String PARTIAL_REFUND = "PARTIAL_REFUND";
    public static final String NO_RENTAL_REFUND = "NO_RENTAL_REFUND";
    public static final String OWNER_CANCELLED = "OWNER_CANCELLED";

    static final String NOTICE = "Draft cancellation policy pending legal review; amounts are configurable.";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CancellationProperties properties;

    public CancellationPolicy(CancellationProperties properties) {
        this.properties = properties;
    }

    /**
     * @param paid           whether the payment was captured (booking {@code CONFIRMED} or later)
     * @param amountPaid     total captured from the renter, deposit included
     * @param deposit        refundable security deposit within {@code amountPaid}
     * @param expectedPayout owner's expected settlement (base rental less commission)
     */
    public CancellationBreakdown forRenter(
            boolean paid,
            BigDecimal amountPaid,
            BigDecimal deposit,
            BigDecimal expectedPayout,
            Instant startTime,
            Instant now
    ) {
        BigDecimal hours = hoursBefore(startTime, now);
        if (!paid) {
            return unpaid("RENTER", hours);
        }
        BigDecimal rentalCharges = amountPaid.subtract(deposit);
        String tier;
        BigDecimal rentalRefund;
        BigDecimal gatewayFee = ZERO;
        BigDecimal ownerCompensation;
        if (hours.compareTo(BigDecimal.valueOf(properties.fullRefundHours())) >= 0) {
            tier = FULL_REFUND;
            gatewayFee = percent(rentalCharges, properties.gatewayFeePercent());
            rentalRefund = rentalCharges.subtract(gatewayFee);
            ownerCompensation = ZERO;
        } else if (hours.compareTo(BigDecimal.valueOf(properties.partialRefundHours())) >= 0) {
            tier = PARTIAL_REFUND;
            rentalRefund = percent(rentalCharges, properties.partialRefundPercent());
            ownerCompensation = percent(expectedPayout, properties.partialOwnerCompensationPercent());
        } else {
            tier = NO_RENTAL_REFUND;
            rentalRefund = ZERO;
            ownerCompensation = percent(expectedPayout, properties.lateOwnerCompensationPercent());
        }
        return new CancellationBreakdown("RENTER", tier, hours, "INR", scale(amountPaid), scale(rentalCharges),
                scale(rentalRefund), gatewayFee, scale(deposit), scale(rentalRefund.add(deposit)), ownerCompensation,
                ZERO, NOTICE);
    }

    public CancellationBreakdown forOwner(boolean paid, BigDecimal amountPaid, BigDecimal deposit,
                                          Instant startTime, Instant now) {
        BigDecimal hours = hoursBefore(startTime, now);
        if (!paid) {
            return unpaid("OWNER", hours);
        }
        BigDecimal rentalCharges = amountPaid.subtract(deposit);
        return new CancellationBreakdown("OWNER", OWNER_CANCELLED, hours, "INR", scale(amountPaid),
                scale(rentalCharges), scale(rentalCharges), ZERO, scale(deposit), scale(amountPaid), ZERO,
                scale(properties.ownerCancellationPlatformCredit()), NOTICE);
    }

    private static CancellationBreakdown unpaid(String cancelledBy, BigDecimal hours) {
        return new CancellationBreakdown(cancelledBy, NO_PAYMENT, hours, "INR", ZERO, ZERO, ZERO, ZERO, ZERO, ZERO,
                ZERO, ZERO, NOTICE);
    }

    private static BigDecimal hoursBefore(Instant startTime, Instant now) {
        return BigDecimal.valueOf(Duration.between(now, startTime).toSeconds())
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.FLOOR);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
