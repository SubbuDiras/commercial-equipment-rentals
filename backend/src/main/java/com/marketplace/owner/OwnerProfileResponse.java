package com.marketplace.owner;

import java.time.Instant;

public record OwnerProfileResponse(
        String ownerProfileId,
        String status,
        Instant submittedAt
) {
}
