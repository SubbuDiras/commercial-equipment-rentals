package com.marketplace.rental;

import java.time.Instant;

public record ConditionApprovalResponse(
        String bookingId,
        String bookingStatus,
        Instant approvedAt
) {
}
