package com.marketplace.equipment;

import java.util.List;

public record EquipmentCategoryResponse(
        String id,
        String slug,
        String name,
        String description,
        List<EquipmentCategoryResponse> subcategories
) {
}
