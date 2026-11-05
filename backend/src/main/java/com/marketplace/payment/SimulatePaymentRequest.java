package com.marketplace.payment;

import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(@NotNull Outcome outcome) {

    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
