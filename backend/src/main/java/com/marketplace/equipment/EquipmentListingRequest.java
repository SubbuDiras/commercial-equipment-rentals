package com.marketplace.equipment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record EquipmentListingRequest(
        @NotBlank String categoryId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Size(max = 100) String manufacturer,
        @NotBlank @Size(max = 100) String modelNumber,
        @Size(max = 100) String serialNumber,
        @Min(1900) @jakarta.validation.constraints.Max(2100) Integer yearOfManufacture,
        @NotBlank @Pattern(regexp = "EXCELLENT|GOOD|FAIR|POOR") String conditionGrade,
        Map<String, Object> specifications,
        @NotNull         @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal baseDailyRate,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal baseWeeklyRate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal securityDeposit,
        @NotNull @Min(1) Integer minimumRentalDays,
        boolean deliverySupported,
        boolean pickupSupported,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal deliveryChargeFlat,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal longitude
) {
}
