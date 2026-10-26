package com.marketplace.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PilotMetricsResponse(
        LocalDate from,
        LocalDate to,
        long bookingRequests,
        long capturedBookings,
        BigDecimal bookingConversionPercent,
        BigDecimal capturedGrossAmount,
        BigDecimal rentalGrossAmount,
        BigDecimal platformCommissionAmount,
        BigDecimal takeRatePercent,
        BigDecimal averageOwnerResponseMinutes,
        long disputes,
        long openDisputes,
        long verifiedOwners,
        long activeListings,
        long pendingSettlements,
        long releasedSettlements
) {
}
