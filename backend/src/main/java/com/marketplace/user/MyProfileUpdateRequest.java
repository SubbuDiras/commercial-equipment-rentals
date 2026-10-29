package com.marketplace.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The details a signed-in user may change about themselves. Email is the sign-in identity and stays in Keycloak. */
public record MyProfileUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        /** Optional; blank clears it. Digits with an optional leading +, e.g. +919876543210. */
        @Pattern(regexp = "^$|\\+?[0-9]{8,15}", message = "must be 8 to 15 digits, optionally starting with +")
        String phone
) {
}
