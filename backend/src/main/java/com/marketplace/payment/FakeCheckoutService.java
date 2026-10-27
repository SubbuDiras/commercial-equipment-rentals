package com.marketplace.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.user.UserProfileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only stand-in for the Razorpay Checkout popup. It records the payment in the fake gateway, signs the
 * result with the dev key secret exactly as Checkout would, and delivers the matching signed webhook through
 * the real webhook handler so that path is exercised too.
 */
@Service
@ConditionalOnProperty(name = "payment.razorpay.fake", havingValue = "true")
public class FakeCheckoutService {

    private static final String FAILURE_DESCRIPTION = "Payment declined by the fake Razorpay simulator.";

    private final FakeRazorpayGateway gateway;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final RazorpayProperties properties;
    private final RazorpayWebhookController webhookController;
    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    public FakeCheckoutService(
            FakeRazorpayGateway gateway,
            RazorpaySignatureVerifier signatureVerifier,
            RazorpayProperties properties,
            RazorpayWebhookController webhookController,
            JdbcTemplate jdbcTemplate,
            UserProfileService userProfileService,
            ObjectMapper objectMapper
    ) {
        this.gateway = gateway;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.webhookController = webhookController;
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.objectMapper = objectMapper;
    }

    public SimulatedCheckoutResponse simulate(String orderId, SimulatePaymentRequest.Outcome outcome, Jwt renterJwt) {
        StoredOrder order = jdbcTemplate.query("""
                        SELECT p.amount, p.currency, b.renter_user_id
                        FROM payments p JOIN bookings b ON b.id = p.booking_id
                        WHERE p.razorpay_order_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? new StoredOrder(resultSet.getBigDecimal("amount"), resultSet.getString("currency"),
                                resultSet.getString("renter_user_id"))
                        : null,
                orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Payment order not found.");
        }
        if (!order.renterUserId().equals(userProfileService.requireExisting(renterJwt.getSubject()))) {
            throw new AccessDeniedException("Only the renter who created the booking may pay for it.");
        }

        boolean success = outcome == SimulatePaymentRequest.Outcome.SUCCESS;
        FakeRazorpayGateway.FakePayment payment = gateway.simulateCheckout(
                orderId, order.amount().movePointRight(2).longValueExact(), order.currency(), success);
        deliverWebhook(payment, success);

        String signature = success
                ? signatureVerifier.sign(orderId + "|" + payment.paymentId(), properties.keySecret())
                : null;
        return new SimulatedCheckoutResponse(orderId, payment.paymentId(), signature, outcome.name(),
                success ? null : FAILURE_DESCRIPTION);
    }

    private void deliverWebhook(FakeRazorpayGateway.FakePayment payment, boolean success) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("id", payment.paymentId());
        entity.put("order_id", payment.orderId());
        entity.put("amount", payment.amountInPaise());
        entity.put("currency", payment.currency());
        entity.put("status", payment.status());
        if (!success) {
            entity.put("error_code", "BAD_REQUEST_ERROR");
            entity.put("error_description", FAILURE_DESCRIPTION);
        }
        byte[] rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsBytes(Map.of(
                    "event", success ? "payment.captured" : "payment.failed",
                    "payload", Map.of("payment", Map.of("entity", entity))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build the fake Razorpay webhook.", exception);
        }
        String webhookSignature = signatureVerifier.sign(
                new String(rawPayload, StandardCharsets.UTF_8), properties.webhookSecret());
        webhookController.handleRazorpayWebhook(webhookSignature, "evt_FAKE" + UUID.randomUUID(), rawPayload);
    }

    private record StoredOrder(BigDecimal amount, String currency, String renterUserId) {
    }
}
