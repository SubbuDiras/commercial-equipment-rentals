package com.marketplace.payment;

public record PaymentOrderResponse(
        String paymentId,
        String razorpayOrderId,
        long amountInPaise,
        String currency,
        String razorpayKeyId
) {
}
