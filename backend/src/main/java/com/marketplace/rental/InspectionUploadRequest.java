package com.marketplace.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InspectionUploadRequest(
        @NotBlank String contentType,
        @Min(1) @Max(10_485_760) long contentLength
) {
}
