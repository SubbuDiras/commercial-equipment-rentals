package com.marketplace.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors what Razorpay Checkout hands the browser. On success, post the three {@code razorpay_*} values to
 * {@code POST /api/v1/payments/{paymentId}/verify}. On failure the signature is null.
 */
public record SimulatedCheckoutResponse(
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        @JsonProperty("razorpay_payment_id") String razorpayPaymentId,
        @JsonProperty("razorpay_signature") String razorpaySignature,
        String outcome,
        @JsonProperty("error_description") String errorDescription
) {
}
