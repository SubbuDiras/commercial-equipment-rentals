package com.marketplace.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Who is bringing the equipment and when it should arrive, recorded by the owner when it leaves. */
public record DeliveryDispatchRequest(
        @NotBlank @Size(max = 100) String driverName,
        /** A 10-digit Indian mobile number with the +91 country code, e.g. +919876543210. */
        @NotBlank
        @Pattern(regexp = "\\+91[6-9][0-9]{9}", message = "must be a 10-digit Indian mobile number, e.g. +919876543210")
        String driverPhone,
        /** An Indian registration number without spaces, e.g. TS09AB1234 or 22BH1234AA. */
        @NotBlank
        @Pattern(regexp = "[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}|[0-9]{2}BH[0-9]{4}[A-Z]{1,2}",
                message = "must be a vehicle registration number, e.g. TS09AB1234")
        String vehicleNumber,
        @NotNull Instant expectedArrivalAt
) {
}
