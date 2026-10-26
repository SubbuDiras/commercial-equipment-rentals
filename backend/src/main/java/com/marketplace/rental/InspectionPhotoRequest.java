package com.marketplace.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InspectionPhotoRequest(
        @NotBlank @Size(max = 512) String storageObjectKey,
        @NotBlank @Size(max = 50) String mediaTag,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
