package com.marketplace.notification;

import java.util.Map;

/**
 * Published inside a business transaction; the email is sent only after it commits.
 *
 * @param subjectId the booking id, or the owner profile id ({@code KYC_*}) or equipment id ({@code LISTING_*})
 * @param details   extra template values, e.g. {@code reason}, {@code breakdown}, {@code code}
 */
public record NotificationEvent(NotificationType type, String subjectId, Map<String, Object> details) {

    public static NotificationEvent of(NotificationType type, String subjectId) {
        return new NotificationEvent(type, subjectId, Map.of());
    }

    public static NotificationEvent of(NotificationType type, String subjectId, Map<String, Object> details) {
        return new NotificationEvent(type, subjectId, details);
    }
}
