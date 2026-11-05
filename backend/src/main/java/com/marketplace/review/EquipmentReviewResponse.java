package com.marketplace.review;

import java.time.Instant;

/**
 * Public view of a renter's review. Only the reviewer's first name and last initial are shown.
 */
public record EquipmentReviewResponse(
        String reviewId,
        int rating,
        String comments,
        String reviewerName,
        Instant createdAt
) {
}
