package com.marketplace.notification;

/**
 * Each type has a template at {@code templates/email/<template>.html}. The subject may use {@code {ref}} (booking
 * reference) and {@code {title}} (equipment title).
 */
public enum NotificationType {
    KYC_SUBMITTED("kyc-submitted", "We received your owner application"),
    KYC_APPROVED("kyc-approved", "Your owner application is approved"),
    KYC_REJECTED("kyc-rejected", "Your owner application needs changes"),
    LISTING_APPROVED("listing-approved", "Your listing \"{title}\" is live"),
    LISTING_REJECTED("listing-rejected", "Your listing \"{title}\" needs changes"),
    BOOKING_REQUESTED("booking-requested", "New booking request {ref} for {title}"),
    BOOKING_ACCEPTED("booking-accepted", "Booking {ref} accepted - complete payment"),
    BOOKING_REJECTED("booking-rejected", "Booking {ref} was declined"),
    PAYMENT_RECEIVED("payment-received", "Payment received - booking {ref} confirmed"),
    BOOKING_CANCELLED("booking-cancelled", "Booking {ref} cancelled"),
    INSPECTION_OTP("inspection-otp", "Your {inspection} code for booking {ref}"),
    DISPUTE_OPENED("dispute-opened", "Damage claim opened on booking {ref}"),
    DISPUTE_RESOLVED("dispute-resolved", "Damage claim on booking {ref} resolved"),
    BOOKING_COMPLETED("booking-completed", "Booking {ref} completed - leave a review");

    private final String template;
    private final String subject;

    NotificationType(String template, String subject) {
        this.template = template;
        this.subject = subject;
    }

    public String template() {
        return "email/" + template;
    }

    public String subject() {
        return subject;
    }
}
