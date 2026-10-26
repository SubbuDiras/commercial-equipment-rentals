package com.marketplace.equipment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EquipmentImageUploadRequest(
        @NotBlank @Pattern(regexp = "image/jpeg|image/png|image/webp") String contentType,
        @Min(1) @Max(10485760) long contentLength
) {
}
