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
        Instant createdAt,
        BigDecimal averageRating,
        Integer reviewCount,
        String primaryImageUrl
) {
    /** Rating figures are filled in on the public details endpoint only; elsewhere they are null. */
    public EquipmentListingResponse withRating(BigDecimal average, int count) {
        return new EquipmentListingResponse(id, categoryId, categoryName, title, description, manufacturer,
                modelNumber, serialNumber, yearOfManufacture, conditionGrade, specifications, baseDailyRate,
                baseWeeklyRate, securityDeposit, minimumRentalDays, deliverySupported, pickupSupported,
                deliveryChargeFlat, addressLine, city, postalCode, latitude, longitude, status, createdAt,
                average, count, primaryImageUrl);
    }

    /** The cover photo's short-lived URL is filled in on search results only; elsewhere it is null. */
    public EquipmentListingResponse withPrimaryImageUrl(String url) {
        return new EquipmentListingResponse(id, categoryId, categoryName, title, description, manufacturer,
                modelNumber, serialNumber, yearOfManufacture, conditionGrade, specifications, baseDailyRate,
                baseWeeklyRate, securityDeposit, minimumRentalDays, deliverySupported, pickupSupported,
                deliveryChargeFlat, addressLine, city, postalCode, latitude, longitude, status, createdAt,
                averageRating, reviewCount, url);
    }
}
