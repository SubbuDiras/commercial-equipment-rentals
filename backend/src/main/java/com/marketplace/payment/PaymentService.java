package com.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.booking.BookingStatus;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.PaymentOperationsDisabledException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.rental.DisputeResolutionRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RazorpayGateway gateway;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final RazorpayProperties properties;
    private final PaymentOperationsProperties operationsProperties;
    private final Environment environment;

    public PaymentService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RazorpayGateway gateway,
            RazorpaySignatureVerifier signatureVerifier,
            RazorpayProperties properties,
            PaymentOperationsProperties operationsProperties,
            Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.operationsProperties = operationsProperties;
        this.environment = environment;
    }

    @Transactional
    public PaymentOrderResponse createOrder(String bookingId, Jwt renterJwt) {
        requireFinancialOperationsEnabled();
        BookingPayment booking = lockBooking(bookingId);
        String renterId = requireUser(renterJwt.getSubject());
        if (!booking.renterUserId().equals(renterId)) {
            throw new AccessDeniedException("Only the renter who created the booking may pay for it.");
        }
        if (booking.status() != BookingStatus.PAYMENT_PENDING && booking.status() != BookingStatus.PAYMENT_FAILED) {
            throw new InvalidStateTransitionException(booking.status().name(), "PAYMENT_ORDER_CREATED");
        }
        if (booking.holdExpiresAt() == null || !booking.holdExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("The payment hold has expired.");
        }

        PaymentOrderResponse previous = findExistingOrder(bookingId);
        if (previous != null) {
            return previous;
        }
        requireGatewayCredentials();
        long amountInPaise = toPaise(booking.totalPayableAmount());
        String receipt = booking.bookingReference();
        RazorpayGateway.RazorpayOrder order = gateway.createOrder(amountInPaise, receipt, "INR");
        if (order.orderId() == null || order.orderId().isBlank()
                || order.amountInPaise() != amountInPaise || !"INR".equals(order.currency())) {
            throw new IllegalStateException("Razorpay returned an order that does not match the booking amount.");
        }
        String paymentId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO payments (id, booking_id, razorpay_order_id, amount, currency, status)
                VALUES (?, ?, ?, ?, 'INR', 'CREATED')
                """,
                paymentId, bookingId, order.orderId(), booking.totalPayableAmount());
        return new PaymentOrderResponse(paymentId, order.orderId(), amountInPaise, "INR", properties.keyId());
    }

    @Transactional
    public PaymentResponse verify(String paymentId, PaymentVerificationRequest request, Jwt renterJwt) {
        requireFinancialOperationsEnabled();
        PaymentRow payment = lockPayment(paymentId);
        String renterId = requireUser(renterJwt.getSubject());
        if (!payment.renterUserId().equals(renterId)) {
            throw new AccessDeniedException("Only the renter who created the booking may verify this payment.");
        }
        if (!payment.orderId().equals(request.razorpayOrderId())) {
            throw new ConflictException("The Razorpay order does not match this payment.");
        }
        if (!signatureVerifier.verify(
                request.razorpayOrderId() + "|" + request.razorpayPaymentId(),
                request.razorpaySignature(),
                properties.keySecret())) {
            throw new ConflictException("Razorpay payment signature is invalid.");
        }

        RazorpayGateway.RazorpayPayment gatewayPayment = gateway.getPayment(request.razorpayPaymentId());
        validateGatewayPayment(payment, gatewayPayment);
        confirmCapturedPayment(payment, request.razorpayPaymentId(), request.razorpaySignature());
        return new PaymentResponse(payment.id(), payment.bookingId(), "CAPTURED", payment.orderId());
    }

    @Transactional
    public void handleWebhook(String eventId, byte[] rawPayload, String suppliedSignature) {
        requireFinancialOperationsEnabled();
        if (eventId == null || eventId.isBlank() || rawPayload == null || rawPayload.length == 0) {
            throw new ConflictException("Razorpay webhook event id and raw request body are required.");
        }
        String secret = properties.webhookSecret();
        if (!signatureVerifier.verify(new String(rawPayload, StandardCharsets.UTF_8), suppliedSignature, secret)) {
            throw new AccessDeniedException("Razorpay webhook signature is invalid.");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawPayload);
        } catch (java.io.IOException exception) {
            throw new ConflictException("Razorpay webhook payload is not valid JSON.");
        }

        String payloadHash = sha256(rawPayload);
        int inserted;
        try {
            inserted = jdbcTemplate.update("""
                    INSERT INTO payment_webhook_events
                        (id, event_id, event_type, payload_hash, raw_payload, status)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """,
                    UUID.randomUUID().toString(), eventId, payload.path("event").asText(),
                    payloadHash, new String(rawPayload, StandardCharsets.UTF_8));
        } catch (DuplicateKeyException exception) {
            WebhookStatus existing = jdbcTemplate.queryForObject("""
                            SELECT status, payload_hash FROM payment_webhook_events WHERE event_id = ?
                            """,
                    (rs, rowNum) -> new WebhookStatus(rs.getString("status"), rs.getString("payload_hash")),
                    eventId);
            if (existing != null && "PROCESSED".equals(existing.status())
                    && payloadHash.equals(existing.payloadHash())) {
                return;
            }
            if (existing != null && !payloadHash.equals(existing.payloadHash())) {
                throw new ConflictException("Razorpay reused an event id for a different payload.");
            }
            throw new ConflictException("This Razorpay webhook event is already being processed.");
        }

        if (inserted != 1) {
            throw new IllegalStateException("Razorpay webhook event was not recorded.");
        }
        String eventType = payload.path("event").asText();
        switch (eventType) {
            case "payment.captured" -> processCapturedWebhook(payload);
            case "payment.failed" -> processFailedWebhook(payload);
            case "transfer.processed" -> processTransferWebhook(payload);
            default -> {
                jdbcTemplate.update("""
                        UPDATE payment_webhook_events SET status = 'PROCESSED', processed_at = ?
                        WHERE event_id = ?
                        """, Timestamp.from(Instant.now()), eventId);
                return;
            }
        }
        jdbcTemplate.update("""
                UPDATE payment_webhook_events SET status = 'PROCESSED', processed_at = ?
                WHERE event_id = ?
                """, Timestamp.from(Instant.now()), eventId);
    }

    @Transactional
    public int releaseMaturedSettlements() {
        requireFinancialOperationsEnabled();
        Instant now = Instant.now();
        var settlements = jdbcTemplate.query("""
                        SELECT s.id, s.booking_id, s.razorpay_transfer_id,
                               p.razorpay_payment_id, b.security_deposit_amount,
                               s.net_payout_amount + s.owner_damage_award_amount AS net_payout_amount,
                               s.platform_commission_amount,
                               op.razorpay_linked_account_id
                        FROM owner_settlements s
                        JOIN bookings b ON b.id = s.booking_id
                        JOIN payments p ON p.booking_id = b.id
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        WHERE b.status = 'INSPECTION_PASSED'
                          AND s.status IN ('ON_HOLD', 'PENDING')
                          AND (s.release_after IS NULL OR s.release_after <= ?)
                        ORDER BY b.updated_at
                        LIMIT 50
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new MaturedSettlement(
                        rs.getString("id"), rs.getString("booking_id"), rs.getString("razorpay_transfer_id"),
                        rs.getString("razorpay_payment_id"),
                        rs.getBigDecimal("security_deposit_amount"), rs.getBigDecimal("net_payout_amount"),
                        rs.getBigDecimal("platform_commission_amount"), rs.getString("razorpay_linked_account_id")),
                Timestamp.from(now));

        int released = 0;
        for (MaturedSettlement settlement : settlements) {
            String paymentId = settlement.razorpayPaymentId();
            BigDecimal deposit = settlement.deposit();
            if (deposit.signum() > 0) {
                Integer resolvedDisputes = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*) FROM disputes
                                WHERE booking_id = ? AND status = 'RESOLVED'
                                """,
                        Integer.class, settlement.bookingId());
                Integer existingRefund = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*) FROM refunds
                                WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                                """,
                        Integer.class, settlement.bookingId());
                if ((resolvedDisputes == null || resolvedDisputes == 0)
                        && (existingRefund == null || existingRefund == 0)) {
                    requireGatewayCredentials();
                    String refundId = gateway.refund(paymentId, toPaise(deposit),
                            "deposit-" + settlement.bookingId());
                    if (refundId == null || refundId.isBlank()) {
                        throw new IllegalStateException("Razorpay did not return an id for the deposit refund.");
                    }
                    jdbcTemplate.update("""
                            INSERT INTO refunds
                                (id, booking_id, payment_id, razorpay_refund_id, amount, refund_type, status)
                            VALUES (?, ?, (SELECT id FROM payments WHERE booking_id = ?), ?, ?, 'SECURITY_DEPOSIT', 'PROCESSED')
                            """,
                            UUID.randomUUID().toString(), settlement.bookingId(), settlement.bookingId(),
                            refundId, deposit);
                }
            }
            String transferId = settlement.transferId();
            if (transferId == null || transferId.isBlank()) {
                if (settlement.linkedAccountId() == null || settlement.linkedAccountId().isBlank()) {
                    continue;
                }
                requireGatewayCredentials();
                Instant releaseAfter = now.plusSeconds(60);
                RazorpayGateway.RazorpayTransfer transfer = gateway.createHeldTransfer(
                        paymentId, settlement.linkedAccountId(), toPaise(settlement.netPayout()),
                        toPaise(settlement.commission()), releaseAfter.getEpochSecond());
                if (transfer.transferId() == null || transfer.transferId().isBlank()) {
                    throw new IllegalStateException("Razorpay did not return an id for the held owner transfer.");
                }
                int updated = jdbcTemplate.update("""
                        UPDATE owner_settlements
                        SET razorpay_transfer_id = ?, status = 'ON_HOLD', release_after = ?
                        WHERE id = ? AND razorpay_transfer_id IS NULL
                        """, transfer.transferId(), Timestamp.from(releaseAfter), settlement.id());
                if (updated != 1) {
                    throw new ConflictException("Owner settlement changed while its transfer was being created.");
                }
                continue;
            }
            requireGatewayCredentials();
            gateway.releaseTransfer(transferId);
            int updatedSettlement = jdbcTemplate.update("""
                    UPDATE owner_settlements SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ? AND status IN ('ON_HOLD', 'PENDING')
                    """, settlement.id());
            if (updatedSettlement != 1) {
                throw new ConflictException("Owner settlement changed while it was being released.");
            }
            int updatedBooking = jdbcTemplate.update("""
                    UPDATE bookings SET status = 'COMPLETED'
                    WHERE id = ? AND status = 'INSPECTION_PASSED'
                    """, settlement.bookingId());
            if (updatedBooking == 1) {
                jdbcTemplate.update("""
                        INSERT INTO booking_status_history
                            (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                        VALUES (?, ?, 'INSPECTION_PASSED', 'COMPLETED', NULL,
                                'Deposit refund and owner transfer released.')
                        """, UUID.randomUUID().toString(), settlement.bookingId());
            }
            released++;
        }
        return released;
    }

    private void processCapturedWebhook(JsonNode payload) {
        JsonNode entity = payload.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText();
        String paymentId = entity.path("id").asText();
        long amount = entity.path("amount").asLong(-1);
        String currency = entity.path("currency").asText();
        if (orderId.isBlank() || paymentId.isBlank()) {
            throw new ConflictException("Razorpay captured-payment event is missing its order or payment id.");
        }
        PaymentRow payment = findPaymentByOrder(orderId);
        validateGatewayAmount(payment, amount, currency);
        confirmCapturedPayment(payment, paymentId, null);
    }

    private void processFailedWebhook(JsonNode payload) {
        JsonNode entity = payload.path("payload").path("payment").path("entity");
        String orderId = entity.path("order_id").asText();
        String paymentId = entity.path("id").asText();
        if (orderId.isBlank() || paymentId.isBlank()) {
            throw new ConflictException("Razorpay failed-payment event is missing its order or payment id.");
        }
        PaymentRow payment = findPaymentByOrder(orderId);
        BookingPayment booking = lockBooking(payment.bookingId());
        if ("CAPTURED".equals(payment.status())) {
            if (!Objects.equals(payment.razorpayPaymentId(), paymentId)) {
                throw new ConflictException("A different Razorpay payment was already captured for this order.");
            }
            return;
        }
        if (booking.status() != BookingStatus.PAYMENT_PENDING && booking.status() != BookingStatus.PAYMENT_FAILED) {
            throw new InvalidStateTransitionException(booking.status().name(), "PAYMENT_FAILED");
        }
        jdbcTemplate.update("""
                UPDATE payments SET status = 'FAILED', razorpay_payment_id = ?, failure_reason = ?
                WHERE id = ? AND status <> 'CAPTURED'
                """,
                paymentId, entity.path("error_description").asText("Payment failed."), payment.id());
        if (booking.status() == BookingStatus.PAYMENT_PENDING) {
            jdbcTemplate.update("UPDATE bookings SET status = 'PAYMENT_FAILED' WHERE id = ?",
                    booking.id());
            addStatusHistory(booking.id(), BookingStatus.PAYMENT_PENDING, BookingStatus.PAYMENT_FAILED,
                    null, "Razorpay reported payment failure.");
        }
    }

    private void processTransferWebhook(JsonNode payload) {
        JsonNode entity = payload.path("payload").path("transfer").path("entity");
        String transferId = entity.path("id").asText();
        String status = entity.path("status").asText();
        if (transferId.isBlank()) {
            throw new ConflictException("Razorpay transfer event is missing its transfer id.");
        }
        String settlementStatus = switch (status.toLowerCase()) {
            case "processed", "pending" -> "ON_HOLD";
            case "failed", "reversed" -> "FAILED";
            default -> throw new ConflictException("Razorpay transfer event has an unsupported status.");
        };
        jdbcTemplate.update("""
                UPDATE owner_settlements SET status = ?
                WHERE razorpay_transfer_id = ? AND status <> 'RELEASED'
                """,
                settlementStatus, transferId);
    }

    private void confirmCapturedPayment(PaymentRow payment, String razorpayPaymentId, String signature) {
        BookingPayment booking = lockBooking(payment.bookingId());
        if ("CAPTURED".equals(payment.status()) && booking.status() == BookingStatus.CONFIRMED) {
            if (!Objects.equals(payment.razorpayPaymentId(), razorpayPaymentId)) {
                throw new ConflictException("A different Razorpay payment was already captured for this order.");
            }
            return;
        }
        if (booking.status() != BookingStatus.PAYMENT_PENDING
                && booking.status() != BookingStatus.PAYMENT_FAILED) {
            throw new InvalidStateTransitionException(booking.status().name(), "CONFIRMED");
        }
        jdbcTemplate.update("""
                UPDATE payments SET status = 'CAPTURED', razorpay_payment_id = ?, razorpay_signature = ?
                WHERE id = ?
                """,
                razorpayPaymentId, signature, payment.id());
        jdbcTemplate.update("UPDATE bookings SET status = 'CONFIRMED' WHERE id = ?", booking.id());
        addStatusHistory(booking.id(), booking.status(), BookingStatus.CONFIRMED, null,
                "Razorpay payment captured and verified.");
        createHeldSettlement(booking);
    }

    private void createHeldSettlement(BookingPayment booking) {
        SettlementInput settlement = jdbcTemplate.queryForObject("""
                        SELECT e.owner_id, b.base_rental_amount, b.owner_commission_amount
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        WHERE b.id = ?
                        """,
                (rs, rowNum) -> new SettlementInput(
                        rs.getString("owner_id"),
                        rs.getBigDecimal("base_rental_amount"),
                        rs.getBigDecimal("owner_commission_amount")),
                booking.id());
        if (settlement.commission() == null) {
            throw new ConflictException("The booking has no snapshotted owner commission.");
        }
        BigDecimal commission = settlement.commission();
        BigDecimal netPayout = settlement.grossRental().subtract(commission);
        jdbcTemplate.update("""
                INSERT INTO owner_settlements
                    (id, booking_id, owner_profile_id, gross_rental_amount,
                     platform_commission_amount, net_payout_amount, razorpay_transfer_id, status, release_after)
                VALUES (?, ?, ?, ?, ?, ?, NULL, 'PENDING', NULL)
                """,
                UUID.randomUUID().toString(), booking.id(), settlement.ownerProfileId(),
                settlement.grossRental(), commission, netPayout);
    }

    private void validateGatewayPayment(PaymentRow payment, RazorpayGateway.RazorpayPayment gatewayPayment) {
        if (!payment.orderId().equals(gatewayPayment.orderId())) {
            throw new ConflictException("The Razorpay payment belongs to a different order.");
        }
        validateGatewayAmount(payment, gatewayPayment.amountInPaise(), gatewayPayment.currency());
        if (!"captured".equalsIgnoreCase(gatewayPayment.status())) {
            throw new ConflictException("Razorpay has not captured this payment.");
        }
    }

    private static void validateGatewayAmount(PaymentRow payment, long amountInPaise, String currency) {
        if (amountInPaise != toPaise(payment.amount()) || !"INR".equals(currency)) {
            throw new ConflictException("Razorpay payment amount or currency does not match the booking.");
        }
    }

    private PaymentOrderResponse findExistingOrder(String bookingId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT id, razorpay_order_id, amount, currency
                            FROM payments WHERE booking_id = ?
                            """,
                    (rs, rowNum) -> new PaymentOrderResponse(
                            rs.getString("id"), rs.getString("razorpay_order_id"),
                            toPaise(rs.getBigDecimal("amount")), rs.getString("currency"), properties.keyId()),
                    bookingId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private PaymentRow lockPayment(String paymentId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT p.id, p.booking_id, p.razorpay_order_id, p.amount, p.status,
                                   p.razorpay_payment_id, b.renter_user_id
                            FROM payments p JOIN bookings b ON b.id = p.booking_id
                            WHERE p.id = ? FOR UPDATE
                            """,
                    PaymentService::mapPayment, paymentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Payment not found.");
        }
    }

    private PaymentRow findPaymentByOrder(String orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT p.id, p.booking_id, p.razorpay_order_id, p.amount, p.status,
                                   p.razorpay_payment_id, b.renter_user_id
                            FROM payments p JOIN bookings b ON b.id = p.booking_id
                            WHERE p.razorpay_order_id = ? FOR UPDATE
                            """,
                    PaymentService::mapPayment, orderId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Payment order not found.");
        }
    }

    private BookingPayment lockBooking(String bookingId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT id, booking_reference, renter_user_id, status, hold_expires_at,
                                   total_payable_amount, start_time, end_time
                            FROM bookings WHERE id = ? FOR UPDATE
                            """,
                    (rs, rowNum) -> {
                        Timestamp hold = rs.getTimestamp("hold_expires_at");
                        return new BookingPayment(
                                rs.getString("id"), rs.getString("booking_reference"),
                                rs.getString("renter_user_id"), BookingStatus.valueOf(rs.getString("status")),
                                hold == null ? null : hold.toInstant(), rs.getBigDecimal("total_payable_amount"),
                                rs.getTimestamp("start_time").toInstant(), rs.getTimestamp("end_time").toInstant());
                    },
                    bookingId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Booking not found.");
        }
    }

    private String requireUser(String subject) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE keycloak_subject_id = ?", String.class, subject);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("User profile not found.");
        }
    }

    private void addStatusHistory(
            String bookingId,
            BookingStatus from,
            BookingStatus to,
            String actorId,
            String reason
    ) {
        jdbcTemplate.update("""
                INSERT INTO booking_status_history
                    (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, from.name(), to.name(), actorId, reason);
    }

    private void requireGatewayCredentials() {
        if (properties.keyId() == null || properties.keyId().isBlank()
                || properties.keySecret() == null || properties.keySecret().isBlank()) {
            throw new IllegalStateException("Razorpay API credentials are not configured.");
        }
    }

    private void requireFinancialOperationsEnabled() {
        if (!financialOperationsEnabled()) {
            throw new PaymentOperationsDisabledException(
                    "Financial operations are disabled; configure the explicit sandbox or production approvals.");
        }
    }

    public boolean financialOperationsEnabled() {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        return operationsProperties.enabled()
                && (!production || (operationsProperties.productionApproved() && !operationsProperties.sandboxOnly()))
                && (production || (operationsProperties.sandboxOnly()
                && properties.keyId() != null && properties.keyId().startsWith("rzp_test_")));
    }

    @Transactional
    public void resolveDispute(String disputeId, DisputeResolutionRequest request, Jwt adminJwt) {
        requireFinancialOperationsEnabled();
        String adminUserId = requireUser(adminJwt.getSubject());
        DisputeForResolution dispute;
        try {
            dispute = jdbcTemplate.queryForObject("""
                            SELECT d.id, d.booking_id, d.claim_amount, d.status, d.raised_by_user_id,
                                   b.security_deposit_amount, b.status AS booking_status,
                                   p.id AS payment_record_id, p.razorpay_payment_id,
                                   s.razorpay_transfer_id
                            FROM disputes d
                            JOIN bookings b ON b.id = d.booking_id
                            JOIN payments p ON p.booking_id = b.id
                            LEFT JOIN owner_settlements s ON s.booking_id = b.id
                            WHERE d.id = ? FOR UPDATE
                            """,
                    (rs, rowNum) -> new DisputeForResolution(
                            rs.getString("id"), rs.getString("booking_id"),
                            rs.getBigDecimal("claim_amount"), rs.getString("status"),
                            rs.getString("raised_by_user_id"), rs.getBigDecimal("security_deposit_amount"),
                            rs.getString("booking_status"), rs.getString("payment_record_id"),
                            rs.getString("razorpay_payment_id"), rs.getString("razorpay_transfer_id")),
                    disputeId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Open dispute not found.");
        }
        if (!"OPEN".equals(dispute.status()) || !"DISPUTED".equals(dispute.bookingStatus())) {
            throw new ConflictException("Only an open dispute on a disputed booking can be resolved.");
        }
        BigDecimal approved = request.approvedDeductionAmount().setScale(2, RoundingMode.HALF_UP);
        if (approved.compareTo(dispute.claimAmount()) > 0 || approved.compareTo(dispute.deposit()) > 0) {
            throw new ConflictException("Approved deduction cannot exceed the damage claim or security deposit.");
        }

        BigDecimal refundAmount = dispute.deposit().subtract(approved);
        int settlementUpdated = jdbcTemplate.update("""
                UPDATE owner_settlements
                SET owner_damage_award_amount = ?
                WHERE booking_id = ? AND status = 'PENDING' AND razorpay_transfer_id IS NULL
                """, approved, dispute.bookingId());
        if (settlementUpdated != 1) {
            throw new ConflictException("The owner settlement is not available for dispute resolution.");
        }
        if (refundAmount.signum() > 0) {
            requireGatewayCredentials();
            String refundId = gateway.refund(dispute.razorpayPaymentId(), toPaise(refundAmount),
                    "dispute-" + dispute.id());
            if (refundId == null || refundId.isBlank()) {
                throw new IllegalStateException("Razorpay did not return an id for the dispute refund.");
            }
            jdbcTemplate.update("""
                    INSERT INTO refunds
                        (id, booking_id, payment_id, razorpay_refund_id, amount, refund_type, status)
                    VALUES (?, ?, ?, ?, ?, 'DISPUTE_BALANCE', 'PROCESSED')
                    """,
                    UUID.randomUUID().toString(), dispute.bookingId(), dispute.paymentRecordId(),
                    refundId, refundAmount);
        }
        jdbcTemplate.update("""
                UPDATE disputes SET status = 'RESOLVED', approved_deduction_amount = ?,
                    resolution_notes = ?, resolved_by_user_id = ?, resolved_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'OPEN'
                """,
                approved, request.resolutionNotes(), adminUserId, dispute.id());
        jdbcTemplate.update("UPDATE bookings SET status = 'INSPECTION_PASSED' WHERE id = ? AND status = 'DISPUTED'",
                dispute.bookingId());
        jdbcTemplate.update("""
                INSERT INTO booking_status_history
                    (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                VALUES (?, ?, 'DISPUTED', 'INSPECTION_PASSED', ?, ?)
                """,
                UUID.randomUUID().toString(), dispute.bookingId(), adminUserId, request.resolutionNotes());
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs
                        (id, actor_user_id, action, entity_name, entity_id, details_json)
                    VALUES (?, ?, 'DISPUTE_RESOLVED', 'disputes', ?, ?)
                    """,
                    UUID.randomUUID().toString(), adminUserId, dispute.id(),
                    objectMapper.writeValueAsString(Map.of(
                            "approvedDeductionAmount", approved,
                            "refundAmount", refundAmount,
                            "notes", request.resolutionNotes())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dispute audit details.", exception);
        }
    }

    private static long toPaise(BigDecimal amount) {
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ConflictException("Payment amount must have no more than two decimal places.");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static PaymentRow mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentRow(rs.getString("id"), rs.getString("booking_id"), rs.getString("razorpay_order_id"),
                rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("razorpay_payment_id"),
                rs.getString("renter_user_id"));
    }

    private record PaymentRow(
            String id,
            String bookingId,
            String orderId,
            BigDecimal amount,
            String status,
            String razorpayPaymentId,
            String renterUserId
    ) {
    }

    private record BookingPayment(
            String id,
            String bookingReference,
            String renterUserId,
            BookingStatus status,
            Instant holdExpiresAt,
            BigDecimal totalPayableAmount,
            Instant startTime,
            Instant endTime
    ) {
    }

    private record SettlementInput(
            String ownerProfileId,
            BigDecimal grossRental,
            BigDecimal commission
    ) {
    }

    private record MaturedSettlement(
            String id,
            String bookingId,
            String transferId,
            String razorpayPaymentId,
            BigDecimal deposit,
            BigDecimal netPayout,
            BigDecimal commission,
            String linkedAccountId
    ) {
    }

    private record WebhookStatus(String status, String payloadHash) {
    }

    private record DisputeForResolution(
            String id,
            String bookingId,
            BigDecimal claimAmount,
            String status,
            String raisedByUserId,
            BigDecimal deposit,
            String bookingStatus,
            String paymentRecordId,
            String razorpayPaymentId,
            String transferId
    ) {
    }
}
