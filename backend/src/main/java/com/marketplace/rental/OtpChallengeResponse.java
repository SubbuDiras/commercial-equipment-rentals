package com.marketplace.rental;

import java.time.Instant;

public record OtpChallengeResponse(String inspectionType, Instant expiresAt, String code) {
}
