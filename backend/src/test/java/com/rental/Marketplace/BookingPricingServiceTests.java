package com.rental.Marketplace;

import com.marketplace.booking.BookingPricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPricingServiceTests {

    private final BookingPricingService pricingService = new BookingPricingService();

    @Test
    void calculatesRentalDeliveryConvenienceFeeOwnerCommissionProvisionalGstAndDeposit() {
        BookingPricingService.Quote quote = pricingService.calculate(
                Instant.parse("2026-11-01T09:00:00Z"),
                Instant.parse("2026-11-04T18:00:00Z"),
                new BigDecimal("1200.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("350.00"),
                new BigDecimal("5000.00"),
                1);

        assertThat(quote.rentalDays()).isEqualTo(4);
        assertThat(quote.baseRentalAmount()).isEqualByComparingTo("4800.00");
        assertThat(quote.deliveryCharge()).isEqualByComparingTo("350.00");
        assertThat(quote.platformFeeAmount()).isEqualByComparingTo("154.50");
        assertThat(quote.ownerCommissionAmount()).isEqualByComparingTo("576.00");
        assertThat(quote.ownerEarningsEstimated()).isEqualByComparingTo("4574.00");
        assertThat(quote.taxAmount()).isEqualByComparingTo("954.81");
        assertThat(quote.securityDepositAmount()).isEqualByComparingTo("5000.00");
        assertThat(quote.totalPayableAmount()).isEqualByComparingTo("11259.31");
    }

    @Test
    void pricesFullWeeksAtWeeklyRateAndRemainingDaysAtDailyRate() {
        BookingPricingService.Quote quote = pricingService.calculate(
                Instant.parse("2026-11-01T09:00:00Z"),
                Instant.parse("2026-11-10T09:00:00Z"),
                new BigDecimal("1200.00"),
                new BigDecimal("7000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1);

        assertThat(quote.rentalDays()).isEqualTo(9);
        assertThat(quote.baseRentalAmount()).isEqualByComparingTo("9400.00");
        assertThat(quote.platformFeeAmount()).isEqualByComparingTo("282.00");
        assertThat(quote.ownerCommissionAmount()).isEqualByComparingTo("1128.00");
        assertThat(quote.ownerEarningsEstimated()).isEqualByComparingTo("8272.00");
    }

    @Test
    void roundsAnyPartialTwentyFourHourPeriodUpToAnotherRentalDay() {
        BookingPricingService.Quote quote = pricingService.calculate(
                Instant.parse("2026-11-01T09:00:00Z"),
                Instant.parse("2026-11-02T09:00:00.000000001Z"),
                new BigDecimal("1200.00"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1);

        assertThat(quote.rentalDays()).isEqualTo(2);
        assertThat(quote.baseRentalAmount()).isEqualByComparingTo("2400.00");
    }

    @Test
    void rejectsInvalidOrUnrepresentablyLongPeriods() {
        assertThatThrownBy(() -> pricingService.calculate(
                Instant.parse("2026-11-02T09:00:00Z"),
                Instant.parse("2026-11-01T09:00:00Z"),
                new BigDecimal("1200.00"), null, BigDecimal.ZERO, BigDecimal.ZERO, 1))
                .isInstanceOf(com.marketplace.exception.ConflictException.class);

        assertThatThrownBy(() -> pricingService.calculate(
                Instant.MIN, Instant.MAX, new BigDecimal("1200.00"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, 1))
                .isInstanceOf(com.marketplace.exception.ConflictException.class)
                .hasMessageContaining("too long");
    }
}
