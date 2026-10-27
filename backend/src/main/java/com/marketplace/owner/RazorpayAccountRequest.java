package com.marketplace.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RazorpayAccountRequest(
        @NotBlank
        @Pattern(regexp = "acc_[A-Za-z0-9]{1,60}", message = "must be a Razorpay linked account id such as acc_XXXXXXXXXXXXXX")
        String linkedAccountId
) {
}
