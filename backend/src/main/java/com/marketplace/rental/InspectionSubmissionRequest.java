package com.marketplace.rental;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record InspectionSubmissionRequest(
        @NotNull @PositiveOrZero BigDecimal hourMeterReading,
        @Size(max = 50) String fuelOrBatteryLevel,
        @NotEmpty Map<@NotBlank String, @NotNull Boolean> checklist,
        @Size(max = 2000) String conditionSummary,
        @NotBlank @Size(min = 6, max = 6) String ownerOtp,
        @NotBlank @Size(min = 6, max = 6) String renterOtp,
        @NotEmpty @Size(min = 4, max = 20) List<@Valid InspectionPhotoRequest> photos
) {
}
