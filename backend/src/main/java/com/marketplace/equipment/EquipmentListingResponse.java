package com.marketplace.equipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EquipmentListingResponse(
        String id,
        String categoryId,
        String categoryName,
        String title,
        String description,
        String manufacturer,
        String modelNumber,
        String serialNumber,
        Integer yearOfManufacture,
        String conditionGrade,
        Map<String, Object> specifications,
        BigDecimal baseDailyRate,
        BigDecimal baseWeeklyRate,
        BigDecimal securityDeposit,
        int minimumRentalDays,
        boolean deliverySupported,
        boolean pickupSupported,
        BigDecimal deliveryChargeFlat,
        String addressLine,
        String city,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        Instant createdAt
) {
}
