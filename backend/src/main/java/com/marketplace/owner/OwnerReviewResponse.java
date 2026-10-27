package com.marketplace.owner;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerReviewResponse(
        String ownerProfileId,
        String firstName,
        String lastName,
        String email,
        String businessName,
        String businessType,
        String status,
        Instant submittedAt,
        Boolean ownerRoleGranted,
        String warning
) {
    public OwnerReviewResponse(
            String ownerProfileId,
            String firstName,
            String lastName,
            String email,
            String businessName,
            String businessType,
            String status,
            Instant submittedAt
    ) {
        this(ownerProfileId, firstName, lastName, email, businessName, businessType, status, submittedAt,
                null, null);
    }

    public OwnerReviewResponse withRoleGrant(boolean granted, String warning) {
        return new OwnerReviewResponse(ownerProfileId, firstName, lastName, email, businessName, businessType,
                status, submittedAt, granted, warning);
    }
}
