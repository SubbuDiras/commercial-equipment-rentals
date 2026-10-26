package com.marketplace.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OwnerOnboardingRequest(
        @NotBlank
        @Pattern(regexp = "INDIVIDUAL|PROPRIETORSHIP|PVT_LTD|PARTNERSHIP")
        String businessType,
        @Size(max = 255)
        String companyName,
        @Pattern(regexp = "^$|[0-9A-Z]{15}")
        String gstin,
        @Pattern(regexp = "^$|[A-Z]{5}[0-9]{4}[A-Z]")
        String pan,
        @NotBlank
        @Size(min = 8, max = 34)
        @Pattern(regexp = "[0-9]+")
        String bankAccountNumber,
        @NotBlank
        @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}")
        String bankIfsc,
        @NotBlank
        @Size(max = 255)
        String bankAccountHolderName,
        @NotBlank
        @Size(max = 100)
        String firstName,
        @NotBlank
        @Size(max = 100)
        String lastName,
        @NotBlank
        @Size(max = 20)
        String phone
) {
}
