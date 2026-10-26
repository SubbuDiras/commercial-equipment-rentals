package com.marketplace.payment;

import java.math.BigDecimal;
import java.util.Map;

public interface RazorpayGateway {

    RazorpayOrder createOrder(long amountInPaise, String receipt, String currency);

    RazorpayPayment getPayment(String paymentId);

    RazorpayTransfer createHeldTransfer(String paymentId, String linkedAccountId, long amountInPaise,
                                        long platformCommissionInPaise, long holdUntilEpochSeconds);

    void releaseTransfer(String transferId);

    String refund(String paymentId, long amountInPaise, String receipt);

    record RazorpayOrder(String orderId, long amountInPaise, String currency) {
    }

    record RazorpayPayment(String paymentId, String orderId, long amountInPaise, String currency, String status) {
    }

    record RazorpayTransfer(String transferId, String status) {
    }
}
