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
        /** What the applicant submitted, for the admin to check against the documents; absent on review results. */
        OwnerKycDetails kyc,
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
            Instant submittedAt,
            OwnerKycDetails kyc
    ) {
        this(ownerProfileId, firstName, lastName, email, businessName, businessType, status, submittedAt, kyc,
                null, null);
    }

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
        this(ownerProfileId, firstName, lastName, email, businessName, businessType, status, submittedAt, null);
    }

    public OwnerReviewResponse withRoleGrant(boolean granted, String warning) {
        return new OwnerReviewResponse(ownerProfileId, firstName, lastName, email, businessName, businessType,
                status, submittedAt, kyc, granted, warning);
    }
}
