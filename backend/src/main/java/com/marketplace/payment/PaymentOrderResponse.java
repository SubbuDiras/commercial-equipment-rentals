package com.marketplace.payment;

/**
 * @param provider {@code FAKE} when the in-process fake gateway is active (the UI shows its simulated checkout),
 *                 otherwise {@code RAZORPAY} (the UI opens Razorpay Checkout)
 */
public record PaymentOrderResponse(
        String paymentId,
        String razorpayOrderId,
        long amountInPaise,
        String currency,
        String razorpayKeyId,
        String provider
) {
}
