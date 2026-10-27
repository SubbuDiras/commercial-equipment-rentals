package com.marketplace.rental;

import java.time.Instant;

/**
 * @param code         the one-time code; returned only under the dev and test profiles, otherwise null
 * @param emailedToCounterparty whether the code was emailed to the other party of the booking
 */
public record OtpChallengeResponse(
        String inspectionType,
        Instant expiresAt,
        String code,
        boolean emailedToCounterparty
) {
}
