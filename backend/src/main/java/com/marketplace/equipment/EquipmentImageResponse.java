package com.marketplace.equipment;

public record EquipmentImageResponse(
        String imageId,
        String imageUrl,
        boolean primary,
        int displayOrder
) {
}
