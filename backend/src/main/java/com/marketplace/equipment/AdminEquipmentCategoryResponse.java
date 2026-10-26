package com.marketplace.equipment;

public record AdminEquipmentCategoryResponse(
        String id,
        String parentCategoryId,
        String slug,
        String name,
        String description,
        boolean active
) {
}
