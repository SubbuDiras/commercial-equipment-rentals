package com.marketplace.exception;

public class PaymentOperationsDisabledException extends RuntimeException {

    public PaymentOperationsDisabledException(String message) {
        super(message);
    }
}
