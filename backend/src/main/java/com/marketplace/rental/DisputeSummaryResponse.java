package com.marketplace.rental;

import java.math.BigDecimal;
import java.time.Instant;

public record DisputeSummaryResponse(
        String disputeId,
        String bookingId,
        String bookingReference,
        String equipmentTitle,
        String category,
        BigDecimal claimAmount,
        BigDecimal securityDeposit,
        String status,
        String raisedByName,
        Instant createdAt
) {
}
