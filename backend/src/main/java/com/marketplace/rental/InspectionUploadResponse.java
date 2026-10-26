package com.marketplace.rental;

import java.time.Instant;

public record InspectionUploadResponse(
        String storageObjectKey,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
