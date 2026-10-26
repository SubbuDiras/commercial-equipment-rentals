package com.marketplace.owner;

import java.time.Instant;

public record OwnerDocumentResponse(
        String documentId,
        String documentType,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String status,
        Instant uploadedAt,
        Instant uploadExpiresAt
) {
}
