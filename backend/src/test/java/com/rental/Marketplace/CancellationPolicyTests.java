package com.rental.Marketplace;

import com.marketplace.booking.CancellationBreakdown;
import com.marketplace.booking.CancellationPolicy;
import com.marketplace.booking.CancellationProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationPolicyTests {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final BigDecimal PAID = new BigDecimal("100.00");
    private static final BigDecimal DEPOSIT = new BigDecimal("20.00");
    private static final BigDecimal EXPECTED_PAYOUT = new BigDecimal("70.40");

    private final CancellationPolicy policy = new CancellationPolicy(new CancellationProperties(
            48, 24, new BigDecimal("50"), new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("80"),
            new BigDecimal("500.00")));

    @Test
    void moreThanFortyEightHoursRefundsRentalLessGatewayFeeAndFullDeposit() {
        CancellationBreakdown breakdown = renterCancels(hoursBefore(72));

        assertThat(breakdown.policyTier()).isEqualTo(CancellationPolicy.FULL_REFUND);
        assertThat(breakdown.rentalCharges()).isEqualByComparingTo("80.00");
        assertThat(breakdown.gatewayFee()).isEqualByComparingTo("1.60");
        assertThat(breakdown.rentalRefund()).isEqualByComparingTo("78.40");
        assertThat(breakdown.depositRefund()).isEqualByComparingTo("20.00");
        assertThat(breakdown.totalRefund()).isEqualByComparingTo("98.40");
        assertThat(breakdown.ownerCompensation()).isZero();
    }

    @Test
    void exactlyFortyEightHoursIsStillTheFullRefundTier() {
        assertThat(renterCancels(hoursBefore(48)).policyTier()).isEqualTo(CancellationPolicy.FULL_REFUND);
        assertThat(renterCancels(NOW.plusSeconds(48 * 3600L - 1)).policyTier())
                .isEqualTo(CancellationPolicy.PARTIAL_REFUND);
    }

    @Test
    void twentyFourToFortyEightHoursRefundsHalfTheRentalAndFullDeposit() {
        CancellationBreakdown breakdown = renterCancels(hoursBefore(30));

        assertThat(breakdown.policyTier()).isEqualTo(CancellationPolicy.PARTIAL_REFUND);
        assertThat(breakdown.rentalRefund()).isEqualByComparingTo("40.00");
        assertThat(breakdown.gatewayFee()).isZero();
        assertThat(breakdown.totalRefund()).isEqualByComparingTo("60.00");
        assertThat(breakdown.ownerCompensation()).isZero();
    }

    @Test
    void underTwentyFourHoursOrNoShowRefundsOnlyTheDepositAndCompensatesOwner() {
        for (Instant start : new Instant[] {hoursBefore(10), NOW.minusSeconds(3600)}) {
            CancellationBreakdown breakdown = renterCancels(start);

            assertThat(breakdown.policyTier()).isEqualTo(CancellationPolicy.NO_RENTAL_REFUND);
            assertThat(breakdown.rentalRefund()).isZero();
            assertThat(breakdown.totalRefund()).isEqualByComparingTo("20.00");
            assertThat(breakdown.ownerCompensation()).isEqualByComparingTo("56.32");
        }
    }

    @Test
    void unpaidBookingsMoveNoMoney() {
        CancellationBreakdown breakdown = policy.forRenter(false, PAID, DEPOSIT, EXPECTED_PAYOUT, hoursBefore(5), NOW);

        assertThat(breakdown.policyTier()).isEqualTo(CancellationPolicy.NO_PAYMENT);
        assertThat(breakdown.totalRefund()).isZero();
        assertThat(breakdown.ownerCompensation()).isZero();
        assertThat(policy.forOwner(false, PAID, DEPOSIT, hoursBefore(5), NOW).platformCredit()).isZero();
    }

    @Test
    void ownerCancellationRefundsEverythingAndRecordsPlatformCredit() {
        CancellationBreakdown breakdown = policy.forOwner(true, PAID, DEPOSIT, hoursBefore(5), NOW);

        assertThat(breakdown.policyTier()).isEqualTo(CancellationPolicy.OWNER_CANCELLED);
        assertThat(breakdown.totalRefund()).isEqualByComparingTo("100.00");
        assertThat(breakdown.gatewayFee()).isZero();
        assertThat(breakdown.platformCredit()).isEqualByComparingTo("500.00");
    }

    @Test
    void amountsFollowConfiguredProperties() {
        CancellationPolicy custom = new CancellationPolicy(new CancellationProperties(
                72, 12, new BigDecimal("25"), BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("100"),
                BigDecimal.ZERO));

        CancellationBreakdown partial = custom.forRenter(true, PAID, DEPOSIT, EXPECTED_PAYOUT, hoursBefore(48), NOW);
        assertThat(partial.policyTier()).isEqualTo(CancellationPolicy.PARTIAL_REFUND);
        assertThat(partial.rentalRefund()).isEqualByComparingTo("20.00");
        assertThat(partial.ownerCompensation()).isEqualByComparingTo("7.04");
    }

    private CancellationBreakdown renterCancels(Instant start) {
        return policy.forRenter(true, PAID, DEPOSIT, EXPECTED_PAYOUT, start, NOW);
    }

    private static Instant hoursBefore(int hours) {
        return NOW.plusSeconds(hours * 3600L);
    }
}
