package com.marketplace.booking;

import java.time.Instant;

/**
 * Result of a cancellation, or of a dry-run quote when {@code cancelledAt} is null.
 *
 * @param bookingStatus     status after cancelling, or the current status for a quote
 * @param razorpayRefundId  refund issued, or null when no money moved (or for a quote)
 */
public record CancellationResponse(
        String bookingId,
        String bookingStatus,
        Instant cancelledAt,
        String razorpayRefundId,
        CancellationBreakdown breakdown
) {
}
