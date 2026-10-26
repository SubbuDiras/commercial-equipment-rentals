package com.marketplace.owner;

import java.time.Instant;

public record OwnerDocumentUploadResponse(
        String documentId,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
