package com.rental.Marketplace;

import com.marketplace.booking.BookingStatus;
import com.marketplace.payment.PaymentOrderResponse;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.PaymentVerificationRequest;
import com.marketplace.payment.RazorpayGateway;
import com.marketplace.payment.RazorpaySignatureVerifier;
import com.marketplace.exception.ConflictException;
import com.marketplace.rental.InspectionPhotoRequest;
import com.marketplace.rental.InspectionSubmissionRequest;
import com.marketplace.rental.InspectionType;
import com.marketplace.rental.OtpChallengeResponse;
import com.marketplace.rental.DamageClaimRequest;
import com.marketplace.rental.DisputeResolutionRequest;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.storage.PrivateObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@ActiveProfiles("test")
class PaymentServiceTests {

    private static final String OWNER_ID = "phase4-owner-user";
    private static final String RENTER_ID = "phase4-renter-user";
    private static final String OWNER_PROFILE_ID = "phase4-owner-profile";
    private static final String CATEGORY_ID = "phase4-category";
    private static final String EQUIPMENT_ID = "phase4-equipment";
    private static final String BOOKING_ID = "phase4-booking";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RentalInspectionService rentalInspectionService;

    @Autowired
    private RazorpaySignatureVerifier signatureVerifier;

    @MockBean
    private RazorpayGateway razorpayGateway;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createPhaseFourSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_media");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_uploads");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_otps");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspections");
        jdbcTemplate.execute("DROP TABLE IF EXISTS disputes");
        jdbcTemplate.execute("DROP TABLE IF EXISTS refunds");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_settlements");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_webhook_events");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS booking_status_history");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS bookings");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment_categories");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id CHAR(36) PRIMARY KEY,
                    keycloak_subject_id VARCHAR(64) NOT NULL UNIQUE,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    phone VARCHAR(20),
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_profiles (
                    id CHAR(36) PRIMARY KEY,
                    user_id CHAR(36) NOT NULL UNIQUE,
                    verification_status VARCHAR(30) NOT NULL,
                    razorpay_linked_account_id VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment_categories (
                    id CHAR(36) PRIMARY KEY,
                    slug VARCHAR(100) NOT NULL,
                    name VARCHAR(150) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment (
                    id CHAR(36) PRIMARY KEY,
                    owner_id CHAR(36) NOT NULL,
                    category_id CHAR(36) NOT NULL,
                    status VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE bookings (
                    id CHAR(36) PRIMARY KEY,
                    booking_reference VARCHAR(32) NOT NULL,
                    renter_user_id CHAR(36) NOT NULL,
                    equipment_id CHAR(36) NOT NULL,
                    start_time TIMESTAMP(6) NOT NULL,
                    end_time TIMESTAMP(6) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    hold_expires_at TIMESTAMP(6),
                    total_payable_amount DECIMAL(12,2) NOT NULL,
                    base_rental_amount DECIMAL(12,2) NOT NULL,
                    owner_commission_amount DECIMAL(12,2),
                    security_deposit_amount DECIMAL(12,2) NOT NULL,
                    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE payments (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL UNIQUE,
                    razorpay_order_id VARCHAR(64) NOT NULL UNIQUE,
                    razorpay_payment_id VARCHAR(64) UNIQUE,
                    razorpay_signature VARCHAR(255),
                    amount DECIMAL(12,2) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    failure_reason CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE payment_webhook_events (
                    id CHAR(36) PRIMARY KEY,
                    event_id VARCHAR(100) NOT NULL UNIQUE,
                    event_type VARCHAR(100) NOT NULL,
                    payload_hash VARCHAR(64) NOT NULL,
                    raw_payload CLOB NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    processed_at TIMESTAMP(6),
                    error_message CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_settlements (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL UNIQUE,
                    owner_profile_id CHAR(36) NOT NULL,
                    gross_rental_amount DECIMAL(12,2) NOT NULL,
                    platform_commission_amount DECIMAL(12,2) NOT NULL,
                    net_payout_amount DECIMAL(12,2) NOT NULL,
                    razorpay_transfer_id VARCHAR(64),
                    owner_damage_award_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    status VARCHAR(30) NOT NULL,
                    released_at TIMESTAMP(6),
                    release_after TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE booking_status_history (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    from_status VARCHAR(30),
                    to_status VARCHAR(30) NOT NULL,
                    changed_by_user_id CHAR(36),
                    reason_or_notes CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspections (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    inspector_user_id CHAR(36) NOT NULL,
                    hour_meter_reading DECIMAL(10,2),
                    fuel_or_battery_level VARCHAR(50),
                    checklist_json CLOB NOT NULL,
                    condition_summary CLOB,
                    otp_verified BOOLEAN NOT NULL,
                    inspected_at TIMESTAMP(6) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_media (
                    id CHAR(36) PRIMARY KEY,
                    inspection_id CHAR(36) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL,
                    media_tag VARCHAR(50) NOT NULL,
                    geo_latitude DECIMAL(10,7),
                    geo_longitude DECIMAL(10,7),
                    captured_at TIMESTAMP(6) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_otps (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    participant_user_id CHAR(36) NOT NULL,
                    code_hash CHAR(64) NOT NULL,
                    expires_at TIMESTAMP(6) NOT NULL,
                    verified_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_uploads (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    uploaded_by_user_id CHAR(36) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL UNIQUE,
                    expires_at TIMESTAMP(6) NOT NULL,
                    attached_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE disputes (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    raised_by_user_id CHAR(36) NOT NULL,
                    dispute_category VARCHAR(50) NOT NULL,
                    claim_amount DECIMAL(12,2) NOT NULL,
                    approved_deduction_amount DECIMAL(12,2) DEFAULT 0.00,
                    description CLOB NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    resolution_notes CLOB,
                    resolved_by_user_id CHAR(36),
                    resolved_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE refunds (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    payment_id CHAR(36) NOT NULL,
                    razorpay_refund_id VARCHAR(64) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    refund_type VARCHAR(50) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    UNIQUE (booking_id, refund_type),
                    UNIQUE (razorpay_refund_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id CHAR(36) PRIMARY KEY,
                    actor_user_id CHAR(36),
                    action VARCHAR(100) NOT NULL,
                    entity_name VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(64) NOT NULL,
                    details_json CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);

        jdbcTemplate.update("""
                INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                OWNER_ID, "phase4-owner", "phase4-owner@example.com", "9876543210", "Owner", "Test",
                RENTER_ID, "phase4-renter", "phase4-renter@example.com", "9876543211", "Renter", "Test",
                "phase4-admin-user", "phase4-admin", "phase4-admin@example.com", "9876543212", "Admin", "Test");
        jdbcTemplate.update("""
                INSERT INTO owner_profiles (id, user_id, verification_status, razorpay_linked_account_id)
                VALUES (?, ?, 'VERIFIED', NULL)
                """, OWNER_PROFILE_ID, OWNER_ID);
        jdbcTemplate.update("INSERT INTO equipment_categories (id, slug, name) VALUES (?, 'tools', 'Tools')", CATEGORY_ID);
        jdbcTemplate.update("INSERT INTO equipment (id, owner_id, category_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                EQUIPMENT_ID, OWNER_PROFILE_ID, CATEGORY_ID);
        jdbcTemplate.update("""
                INSERT INTO bookings
                    (id, booking_reference, renter_user_id, equipment_id, start_time, end_time,
                     status, hold_expires_at, total_payable_amount, base_rental_amount,
                     owner_commission_amount, security_deposit_amount)
                VALUES (?, 'BKG-PHASE4-001', ?, ?, ?, ?, 'PAYMENT_PENDING', ?, 100.00, 80.00, 9.60, 20.00)
                """,
                BOOKING_ID, RENTER_ID, EQUIPMENT_ID,
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
        when(razorpayGateway.createOrder(anyLong(), anyString(), anyString()))
                .thenReturn(new RazorpayGateway.RazorpayOrder("order_phase4", 10_000, "INR"));
        when(razorpayGateway.getPayment(anyString()))
                .thenReturn(new RazorpayGateway.RazorpayPayment(
                        "pay_phase4", "order_phase4", 10_000, "INR", "captured"));
        when(razorpayGateway.createHeldTransfer(anyString(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new RazorpayGateway.RazorpayTransfer("transfer_phase4", "pending"));
        when(razorpayGateway.refund(anyString(), anyLong(), anyString())).thenReturn("refund_phase4");
        when(privateObjectStorage.presignPut(anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> new PrivateObjectStorage.PresignedUpload(
                        "https://private.test/upload", Instant.now().plusSeconds(600)));
        when(privateObjectStorage.existsWithinLimit(anyString())).thenReturn(true);
        clearInvocations(razorpayGateway, privateObjectStorage);
    }

    @Test
    void createsPaymentOrderFromServerPriceAndReturnsSameOrderOnRetry() {
        PaymentOrderResponse first = paymentService.createOrder(BOOKING_ID, renterJwt());
        PaymentOrderResponse second = paymentService.createOrder(BOOKING_ID, renterJwt());

        assertThat(first.razorpayOrderId()).isEqualTo("order_phase4");
        assertThat(first.amountInPaise()).isEqualTo(10_000);
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        verify(razorpayGateway).createOrder(10_000, "BKG-PHASE4-001", "INR");
    }

    @Test
    void rejectsForgedClientSignatureWithoutCallingGatewayOrConfirmingBooking() {
        PaymentOrderResponse order = paymentService.createOrder(BOOKING_ID, renterJwt());
        assertThatThrownBy(() -> paymentService.verify(order.paymentId(),
                new PaymentVerificationRequest("order_phase4", "pay_phase4", "forged"), renterJwt()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("signature");
        verify(razorpayGateway, never()).getPayment(anyString());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.PAYMENT_PENDING.name());
    }

    @Test
    void verifiesCapturedPaymentAgainstRazorpayAndCreatesOneSettlement() {
        jdbcTemplate.update("""
                UPDATE bookings
                SET base_rental_amount = 100.00, owner_commission_amount = 5.00
                WHERE id = ?
                """, BOOKING_ID);
        PaymentOrderResponse order = paymentService.createOrder(BOOKING_ID, renterJwt());
        String signature = signatureVerifier.sign("order_phase4|pay_phase4", "test-key-secret");

        paymentService.verify(order.paymentId(),
                new PaymentVerificationRequest("order_phase4", "pay_phase4", signature), renterJwt());
        paymentService.verify(order.paymentId(),
                new PaymentVerificationRequest("order_phase4", "pay_phase4", signature), renterJwt());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.CONFIRMED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?",
                String.class, order.paymentId())).isEqualTo("CAPTURED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owner_settlements WHERE booking_id = ?",
                Integer.class, BOOKING_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT net_payout_amount FROM owner_settlements WHERE booking_id = ?
                """, BigDecimal.class, BOOKING_ID)).isEqualByComparingTo("95.00");
    }

    @Test
    void validDuplicateWebhookIsRecordedAndAppliedExactlyOnce() {
        PaymentOrderResponse order = paymentService.createOrder(BOOKING_ID, renterJwt());
        String raw = """
                {"event":"payment.captured","payload":{"payment":{"entity":{
                  "id":"pay_webhook","order_id":"order_phase4","amount":10000,"currency":"INR"
                }}}}
                """;
        String signature = signatureVerifier.sign(raw, "test-webhook-secret");

        paymentService.handleWebhook("evt_phase4_1", raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), signature);
        paymentService.handleWebhook("evt_phase4_1", raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), signature);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.CONFIRMED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = ?",
                Integer.class, "evt_phase4_1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owner_settlements WHERE booking_id = ?",
                Integer.class, BOOKING_ID)).isEqualTo(1);
    }

    @Test
    void capturedWebhookRejectsASecondPaymentIdForTheSameOrder() {
        paymentService.createOrder(BOOKING_ID, renterJwt());
        String firstPayload = capturedPaymentPayload("pay_webhook_first");
        paymentService.handleWebhook("evt_phase4_first", firstPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signatureVerifier.sign(firstPayload, "test-webhook-secret"));

        String secondPayload = capturedPaymentPayload("pay_webhook_second");
        assertThatThrownBy(() -> paymentService.handleWebhook(
                "evt_phase4_second", secondPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signatureVerifier.sign(secondPayload, "test-webhook-secret")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different Razorpay payment");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owner_settlements WHERE booking_id = ?",
                Integer.class, BOOKING_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = ?",
                Integer.class, "evt_phase4_second")).isZero();
    }

    @Test
    void handoverAndReturnRequireBothOneTimeCodesAndFourPrivatePhotos() {
        jdbcTemplate.update("UPDATE bookings SET status = 'CONFIRMED', start_time = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), BOOKING_ID);
        OtpChallengeResponse ownerHandoverOtp = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, ownerJwt());
        OtpChallengeResponse renterHandoverOtp = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, renterJwt());
        List<InspectionPhotoRequest> handoverPhotos = photos(InspectionType.HANDOVER);

        rentalInspectionService.submit(BOOKING_ID, InspectionType.HANDOVER,
                inspection(ownerHandoverOtp.code(), renterHandoverOtp.code(), handoverPhotos), ownerJwt());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.IN_PROGRESS.name());

        jdbcTemplate.update("UPDATE bookings SET status = 'RETURN_PENDING', end_time = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), BOOKING_ID);
        OtpChallengeResponse ownerReturnOtp = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, ownerJwt());
        OtpChallengeResponse renterReturnOtp = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, renterJwt());
        List<InspectionPhotoRequest> returnPhotos = photos(InspectionType.RETURN);

        rentalInspectionService.submit(BOOKING_ID, InspectionType.RETURN,
                inspection(ownerReturnOtp.code(), renterReturnOtp.code(), returnPhotos), renterJwt());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.RETURNED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rental_inspection_media",
                Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM rental_inspections WHERE otp_verified = TRUE
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void releasesHeldTransferAndRefundsDepositAfterInspectionPass() {
        jdbcTemplate.update("UPDATE bookings SET status = 'INSPECTION_PASSED' WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO payments
                    (id, booking_id, razorpay_order_id, razorpay_payment_id, amount, currency, status)
                VALUES ('phase4-payment', ?, 'order-phase4-settlement', 'pay-phase4-settlement',
                        100.00, 'INR', 'CAPTURED')
                """, BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO owner_settlements
                    (id, booking_id, owner_profile_id, gross_rental_amount, platform_commission_amount,
                     net_payout_amount, razorpay_transfer_id, status)
                VALUES ('phase4-settlement', ?, ?, 80.00, 9.60, 70.40, 'transfer_phase4', 'ON_HOLD')
                """, BOOKING_ID, OWNER_PROFILE_ID);

        assertThat(paymentService.releaseMaturedSettlements()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT amount FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, BigDecimal.class, BOOKING_ID)).isEqualByComparingTo("20.00");
        verify(razorpayGateway).releaseTransfer("transfer_phase4");
        verify(razorpayGateway).refund("pay-phase4-settlement", 2_000, "deposit-" + BOOKING_ID);
    }

    @Test
    void completeDisputedRentalLifecycleRefundsOnlyTheUndisputedDepositBalance() {
        paymentService.createOrder(BOOKING_ID, renterJwt());
        String captured = capturedPaymentPayload("pay_phase4");
        paymentService.handleWebhook("evt_lifecycle_capture",
                captured.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signatureVerifier.sign(captured, "test-webhook-secret"));

        OtpChallengeResponse ownerHandover = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, ownerJwt());
        OtpChallengeResponse renterHandover = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, renterJwt());
        rentalInspectionService.submit(BOOKING_ID, InspectionType.HANDOVER,
                inspection(ownerHandover.code(), renterHandover.code(), photos(InspectionType.HANDOVER)), ownerJwt());

        jdbcTemplate.update("UPDATE bookings SET end_time = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), BOOKING_ID);
        OtpChallengeResponse ownerReturn = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, ownerJwt());
        OtpChallengeResponse renterReturn = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, renterJwt());
        rentalInspectionService.submit(BOOKING_ID, InspectionType.RETURN,
                inspection(ownerReturn.code(), renterReturn.code(), photos(InspectionType.RETURN)), renterJwt());

        String disputeId = rentalInspectionService.raiseDamageClaim(BOOKING_ID,
                new DamageClaimRequest("DAMAGE", new BigDecimal("5.00"), "Documented equipment damage."),
                ownerJwt());
        jdbcTemplate.update("""
                UPDATE rental_inspections SET inspected_at = ?
                WHERE booking_id = ? AND inspection_type = 'RETURN'
                """, java.sql.Timestamp.from(Instant.now().minusSeconds(25 * 60 * 60L)), BOOKING_ID);
        assertThat(rentalInspectionService.advanceTimedStates()).isZero();
        assertThat(paymentService.releaseMaturedSettlements()).isZero();

        paymentService.resolveDispute(disputeId,
                new DisputeResolutionRequest(new BigDecimal("5.00"), "Evidence supports the claim."),
                adminJwt());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo("INSPECTION_PASSED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT owner_damage_award_amount FROM owner_settlements WHERE booking_id = ?
                """, BigDecimal.class, BOOKING_ID)).isEqualByComparingTo("5.00");
        jdbcTemplate.update("UPDATE owner_settlements SET release_after = ? WHERE booking_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), BOOKING_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isZero();

        jdbcTemplate.update("UPDATE owner_profiles SET razorpay_linked_account_id = 'acct_phase4' WHERE id = ?",
                OWNER_PROFILE_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        jdbcTemplate.update("UPDATE owner_settlements SET release_after = ? WHERE booking_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), BOOKING_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT amount FROM refunds WHERE booking_id = ? AND refund_type = 'DISPUTE_BALANCE'
                """, BigDecimal.class, BOOKING_ID)).isEqualByComparingTo("15.00");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, Integer.class, BOOKING_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo("COMPLETED");
        verify(razorpayGateway).refund("pay_phase4", 1_500, "dispute-" + disputeId);
        verify(razorpayGateway).createHeldTransfer(
                eq("pay_phase4"), eq("acct_phase4"), eq(7_540L), eq(960L), anyLong());
        verify(razorpayGateway).releaseTransfer("transfer_phase4");
    }

    @Test
    void refundsDepositOnceEvenWhenOwnerLinkedAccountIsNotYetConfigured() {
        jdbcTemplate.update("UPDATE bookings SET status = 'INSPECTION_PASSED' WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO payments
                    (id, booking_id, razorpay_order_id, razorpay_payment_id, amount, currency, status)
                VALUES ('phase4-payment', ?, 'order-phase4-settlement', 'pay-phase4-settlement',
                        100.00, 'INR', 'CAPTURED')
                """, BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO owner_settlements
                    (id, booking_id, owner_profile_id, gross_rental_amount, platform_commission_amount,
                     net_payout_amount, razorpay_transfer_id, status)
                VALUES ('phase4-settlement', ?, ?, 80.00, 9.60, 70.40, NULL, 'PENDING')
                """, BOOKING_ID, OWNER_PROFILE_ID);

        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, Integer.class, BOOKING_ID)).isEqualTo(1);
        verify(razorpayGateway).refund("pay-phase4-settlement", 2_000, "deposit-" + BOOKING_ID);
        verify(razorpayGateway, never()).releaseTransfer(anyString());

        jdbcTemplate.update("UPDATE owner_profiles SET razorpay_linked_account_id = 'acct_phase4' WHERE id = ?",
                OWNER_PROFILE_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT razorpay_transfer_id FROM owner_settlements WHERE booking_id = ?
                """, String.class, BOOKING_ID)).isEqualTo("transfer_phase4");
        jdbcTemplate.update("UPDATE owner_settlements SET release_after = ? WHERE booking_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), BOOKING_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, Integer.class, BOOKING_ID)).isEqualTo(1);
        verify(razorpayGateway).releaseTransfer("transfer_phase4");
    }

    private static String capturedPaymentPayload(String paymentId) {
        return """
                {"event":"payment.captured","payload":{"payment":{"entity":{
                  "id":"%s","order_id":"order_phase4","amount":10000,"currency":"INR"
                }}}}
                """.formatted(paymentId);
    }

    private List<InspectionPhotoRequest> photos(InspectionType type) {
        return java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> {
                    var upload = rentalInspectionService.presignPhoto(
                            BOOKING_ID, type, "image/jpeg", 256_000, renterJwt());
                    return new InspectionPhotoRequest(upload.storageObjectKey(), "CONDITION", null, null);
                })
                .toList();
    }

    private static InspectionSubmissionRequest inspection(
            String ownerOtp,
            String renterOtp,
            List<InspectionPhotoRequest> photos
    ) {
        return new InspectionSubmissionRequest(
                new BigDecimal("120.5"),
                "FULL",
                Map.of("accessories_present", true, "condition_acceptable", true),
                "Inspection evidence recorded.",
                ownerOtp,
                renterOtp,
                photos);
    }

    private static Jwt renterJwt() {
        return Jwt.withTokenValue("phase4-renter-token")
                .header("alg", "none")
                .subject("phase4-renter")
                .build();
    }

    private static Jwt ownerJwt() {
        return Jwt.withTokenValue("phase4-owner-token")
                .header("alg", "none")
                .subject("phase4-owner")
                .build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("phase4-admin-token")
                .header("alg", "none")
                .subject("phase4-admin")
                .build();
    }
}
