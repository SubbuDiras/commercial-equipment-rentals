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
        @NotBlank
        @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "must look like ABCDE1234F")
        String pan,
        @NotBlank
        @Pattern(regexp = "[0-9]{10}", message = "must be exactly 10 digits")
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
        /** A 10-digit Indian mobile number with the +91 country code, e.g. +919876543210. */
        @NotBlank
        @Pattern(regexp = "\\+91[6-9][0-9]{9}", message = "must be a 10-digit Indian mobile number, e.g. +919876543210")
        String phone,
        /** Checked in full (format and checksum), stored as the last 4 digits only. */
        @NotBlank
        @Pattern(regexp = "[2-9][0-9]{11}")
        String aadhaarNumber,
        @NotBlank
        @Size(max = 100)
        String bankName,
        @NotBlank
        @Pattern(regexp = "SAVINGS|CURRENT")
        String bankAccountType
) {
}
