package com.marketplace.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The details a signed-in user may change about themselves. Email is the sign-in identity and stays in Keycloak. */
public record MyProfileUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        /** Optional; blank clears it. A 10-digit Indian mobile number with the +91 country code, e.g. +919876543210. */
        @Pattern(regexp = "^$|\\+91[6-9][0-9]{9}", message = "must be a 10-digit Indian mobile number, e.g. +919876543210")
        String phone
) {
}
