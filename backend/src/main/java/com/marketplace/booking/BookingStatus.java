package com.marketplace.booking;

import java.time.Instant;

public enum BookingStatus {
    REQUESTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    HANDOVER_PENDING,
    IN_PROGRESS,
    RETURN_PENDING,
    RETURNED,
    DISPUTED,
    INSPECTION_PASSED,
    COMPLETED,
    CLOSED,
    CANCELLED_BY_RENTER,
    CANCELLED_BY_OWNER,
    OWNER_REJECTED,
    REQUEST_EXPIRED,
    HOLD_EXPIRED;

    public boolean blocksInventory(Instant holdExpiresAt, Instant now) {
        return switch (this) {
            case REQUESTED, PAYMENT_PENDING, PAYMENT_FAILED ->
                    holdExpiresAt != null && holdExpiresAt.isAfter(now);
            case CONFIRMED, HANDOVER_PENDING, IN_PROGRESS, RETURN_PENDING, RETURNED, DISPUTED, INSPECTION_PASSED -> true;
            case COMPLETED, CLOSED, CANCELLED_BY_RENTER, CANCELLED_BY_OWNER,
                    OWNER_REJECTED, REQUEST_EXPIRED, HOLD_EXPIRED -> false;
        };
    }
}
