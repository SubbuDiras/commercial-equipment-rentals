package com.marketplace.owner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OwnerDocumentUploadRequest(
        @NotBlank @Pattern(regexp = "IDENTITY|BUSINESS_REGISTRATION|GST_CERTIFICATE|PAN_CARD|BANK_PROOF|ADDRESS_PROOF|OTHER")
        String documentType,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Pattern(regexp = "application/pdf|image/jpeg|image/png")
        String contentType,
        @Min(1) @Max(10485760) long contentLength
) {
}
