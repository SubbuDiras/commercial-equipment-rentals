package com.marketplace.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryAddressRequest(
        @NotBlank @Size(max = 255) String siteContactName,
        @NotBlank @Size(max = 20) String siteContactPhone,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode
) {
}
