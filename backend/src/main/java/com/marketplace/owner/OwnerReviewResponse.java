package com.marketplace.owner;

import java.time.Instant;

public record OwnerReviewResponse(
        String ownerProfileId,
        String firstName,
        String lastName,
        String email,
        String businessName,
        String businessType,
        String status,
        Instant submittedAt
) {
}
