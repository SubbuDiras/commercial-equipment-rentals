package com.marketplace.booking;

/**
 * Next steps the current viewer may take on a booking. The UI shows only these, so it never
 * duplicates the booking state machine.
 */
public enum BookingAction {
    PAY,
    ACCEPT,
    REJECT,
    GENERATE_HANDOVER_OTP,
    UPLOAD_HANDOVER_PHOTOS,
    SUBMIT_HANDOVER,
    GENERATE_RETURN_OTP,
    UPLOAD_RETURN_PHOTOS,
    SUBMIT_RETURN,
    APPROVE_CONDITION,
    RAISE_DISPUTE,
    RESOLVE_DISPUTE,
    CANCEL,
    REVIEW
}
