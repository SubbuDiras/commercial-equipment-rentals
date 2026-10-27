-- Phase 4: booking cancellation (draft policy, pending legal review).

-- Owner cancellation penalty. The platform credit owed to the renter is recorded in booking_cancellations
-- and the audit log only; a credit ledger is out of scope for now.
ALTER TABLE owner_profiles
    ADD COLUMN cancellation_penalty_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_cancellation_penalty_at TIMESTAMP(6) NULL;

-- One row per cancelled booking with the refund breakdown that was applied.
CREATE TABLE booking_cancellations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    booking_id CHAR(36) NOT NULL,
    cancelled_by_user_id CHAR(36) NOT NULL,
    cancelled_by VARCHAR(10) NOT NULL,
    reason TEXT NOT NULL,
    policy_tier VARCHAR(30) NOT NULL,
    hours_before_start DECIMAL(10, 2) NOT NULL,
    amount_paid DECIMAL(12, 2) NOT NULL,
    rental_charges DECIMAL(12, 2) NOT NULL,
    rental_refund DECIMAL(12, 2) NOT NULL,
    gateway_fee DECIMAL(12, 2) NOT NULL,
    deposit_refund DECIMAL(12, 2) NOT NULL,
    total_refund DECIMAL(12, 2) NOT NULL,
    owner_compensation DECIMAL(12, 2) NOT NULL,
    platform_credit DECIMAL(12, 2) NOT NULL,
    razorpay_refund_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_cancellation_booking UNIQUE (booking_id),
    CONSTRAINT fk_cancellation_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_cancellation_user FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
