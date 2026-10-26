package com.marketplace.booking;

import java.math.BigDecimal;

public record BookingPricing(
        String currency,
        int rentalDays,
        BigDecimal baseRentalAmount,
        BigDecimal deliveryCharge,
        BigDecimal platformFeeAmount,
        BigDecimal taxAmount,
        BigDecimal securityDepositAmount,
        BigDecimal totalPayableAmount
) {
}
