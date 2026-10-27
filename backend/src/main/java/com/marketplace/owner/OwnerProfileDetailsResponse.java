package com.marketplace.owner;

import java.time.Instant;

public record OwnerProfileDetailsResponse(
        String ownerProfileId,
        String status,
        String businessType,
        String businessName,
        String rejectionReason,
        boolean razorpayAccountLinked,
        Instant submittedAt,
        Instant verifiedAt
) {
}
