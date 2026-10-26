package com.marketplace.payment;

public record PaymentResponse(
        String paymentId,
        String bookingId,
        String status,
        String razorpayOrderId
) {
}
