package com.marketplace.payment;

import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for Razorpay so the full payment, refund and settlement flow runs locally without an
 * account. Enabled by {@code payment.razorpay.fake=true}; refuses to start unless the dev or test profile is
 * active and the prod profile is not. State is lost on restart.
 */
@Component
@ConditionalOnProperty(name = "payment.razorpay.fake", havingValue = "true")
public class FakeRazorpayGateway implements RazorpayGateway {

    public static final String CAPTURED = "captured";
    public static final String FAILED = "failed";

    private static final Logger log = LoggerFactory.getLogger(FakeRazorpayGateway.class);
    private static final String ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, RazorpayOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, FakePayment> payments = new ConcurrentHashMap<>();
    private final Map<String, FakeTransfer> transfers = new ConcurrentHashMap<>();
    private final Map<String, Long> refundedByPayment = new ConcurrentHashMap<>();

    public FakeRazorpayGateway(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                || !environment.acceptsProfiles(Profiles.of("dev | test"))) {
            throw new IllegalStateException(
                    "payment.razorpay.fake=true is only allowed with the dev or test profile.");
        }
        log.warn("Using the in-process FAKE Razorpay gateway. No real payments are made.");
    }

    @Override
    public RazorpayOrder createOrder(long amountInPaise, String receipt, String currency) {
        RazorpayOrder order = new RazorpayOrder(newId("order_"), amountInPaise, currency);
        orders.put(order.orderId(), order);
        return order;
    }

    @Override
    public RazorpayPayment getPayment(String paymentId) {
        FakePayment payment = payments.get(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Fake Razorpay payment not found: " + paymentId);
        }
        RazorpayOrder order = orders.get(payment.orderId());
        return new RazorpayPayment(paymentId, order.orderId(), order.amountInPaise(), order.currency(),
                payment.status());
    }

    @Override
    public RazorpayTransfer createHeldTransfer(
            String paymentId,
            String linkedAccountId,
            long amountInPaise,
            long platformCommissionInPaise,
            long holdUntilEpochSeconds
    ) {
        warnIfUnknownPayment(paymentId, "transfer");
        String transferId = newId("trf_");
        transfers.put(transferId, new FakeTransfer(paymentId, linkedAccountId, amountInPaise, false));
        return new RazorpayTransfer(transferId, "pending");
    }

    @Override
    public void releaseTransfer(String transferId) {
        FakeTransfer transfer = transfers.get(transferId);
        if (transfer == null) {
            // Transfers created before a backend restart are not in memory; treat the release as successful.
            log.warn("Fake Razorpay is releasing unknown transfer {} (created before a restart?).", transferId);
            return;
        }
        transfers.put(transferId, transfer.markReleased());
    }

    @Override
    public synchronized String refund(String paymentId, long amountInPaise, String receipt) {
        FakePayment payment = payments.get(paymentId);
        if (payment == null) {
            warnIfUnknownPayment(paymentId, "refund");
        } else {
            long alreadyRefunded = refundedByPayment.getOrDefault(paymentId, 0L);
            long captured = orders.get(payment.orderId()).amountInPaise();
            if (!CAPTURED.equals(payment.status()) || alreadyRefunded + amountInPaise > captured) {
                throw new ConflictException("Fake Razorpay refund exceeds the captured amount.");
            }
            refundedByPayment.put(paymentId, alreadyRefunded + amountInPaise);
        }
        return newId("rfnd_");
    }

    /**
     * Plays the part of Razorpay Checkout: records a captured or failed payment against an order.
     * The stored order amount is passed in so orders created before a backend restart can still be paid.
     */
    public FakePayment simulateCheckout(String orderId, long amountInPaise, String currency, boolean success) {
        RazorpayOrder order = orders.computeIfAbsent(orderId,
                id -> new RazorpayOrder(id, amountInPaise, currency));
        if (order.amountInPaise() != amountInPaise || !order.currency().equals(currency)) {
            throw new ConflictException("Fake Razorpay order amount does not match the stored payment.");
        }
        FakePayment payment = new FakePayment(newId("pay_"), orderId, order.amountInPaise(), order.currency(),
                success ? CAPTURED : FAILED);
        payments.put(payment.paymentId(), payment);
        return payment;
    }

    public long refundedInPaise(String paymentId) {
        return refundedByPayment.getOrDefault(paymentId, 0L);
    }

    public boolean isTransferReleased(String transferId) {
        FakeTransfer transfer = transfers.get(transferId);
        return transfer != null && transfer.released();
    }

    private void warnIfUnknownPayment(String paymentId, String operation) {
        if (!payments.containsKey(paymentId)) {
            // Payments made before a backend restart are not in memory; let dev data keep flowing.
            log.warn("Fake Razorpay {} for unknown payment {} (made before a restart?); accepting it.",
                    operation, paymentId);
        }
    }

    private String newId(String prefix) {
        StringBuilder id = new StringBuilder(prefix).append("FAKE");
        for (int index = 0; index < 10; index++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    public record FakePayment(String paymentId, String orderId, long amountInPaise, String currency, String status) {
    }

    private record FakeTransfer(String paymentId, String linkedAccountId, long amountInPaise, boolean released) {
        FakeTransfer markReleased() {
            return new FakeTransfer(paymentId, linkedAccountId, amountInPaise, true);
        }
    }
}
