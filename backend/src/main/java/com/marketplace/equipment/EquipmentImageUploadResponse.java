package com.marketplace.equipment;

import java.time.Instant;

public record EquipmentImageUploadResponse(
        String imageId,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
