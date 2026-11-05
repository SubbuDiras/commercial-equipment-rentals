package com.marketplace.review;

import java.time.Instant;

/**
 * @param reviewerRole  {@code RENTER} (reviewing the owner and equipment) or {@code OWNER} (reviewing the renter)
 * @param bookingStatus booking status after the review; {@code CLOSED} once both sides have reviewed
 */
public record ReviewResponse(
        String reviewId,
        String bookingId,
        String reviewerRole,
        String reviewerName,
        int rating,
        String comments,
        Instant createdAt,
        String bookingStatus
) {
    ReviewResponse withBookingStatus(String status) {
        return new ReviewResponse(reviewId, bookingId, reviewerRole, reviewerName, rating, comments, createdAt, status);
    }
}
