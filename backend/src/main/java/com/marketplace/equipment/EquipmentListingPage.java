package com.marketplace.equipment;

import java.util.List;

public record EquipmentListingPage(
        List<EquipmentListingResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
