package com.rental.Marketplace;

import com.marketplace.booking.BookingAction;
import com.marketplace.booking.BookingDetailsResponse;
import com.marketplace.booking.BookingDetailsService;
import com.marketplace.booking.BookingStatus;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.payment.PaymentOrderResponse;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.PaymentVerificationRequest;
import com.marketplace.payment.RazorpayGateway;
import com.marketplace.payment.RazorpaySignatureVerifier;
import com.marketplace.exception.ConflictException;
import com.marketplace.rental.AdminDisputeService;
import com.marketplace.rental.ConditionApprovalResponse;
import com.marketplace.rental.DisputeDetailResponse;
import com.marketplace.rental.DisputeStatus;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    private static final String OWNER_ID = RentalLifecycleSchema.OWNER_ID;
    private static final String RENTER_ID = RentalLifecycleSchema.RENTER_ID;
    private static final String OWNER_PROFILE_ID = RentalLifecycleSchema.OWNER_PROFILE_ID;
    private static final String BOOKING_ID = RentalLifecycleSchema.BOOKING_ID;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RentalInspectionService rentalInspectionService;

    @Autowired
    private RazorpaySignatureVerifier signatureVerifier;

    @Autowired
    private BookingDetailsService bookingDetailsService;

    @Autowired
    private AdminDisputeService adminDisputeService;

    @MockBean
    private RazorpayGateway razorpayGateway;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createPhaseFourSchema() {
        RentalLifecycleSchema.create(jdbcTemplate);
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

    @Test
    void ownerApprovesReturnedConditionOnlyOnce() {
        jdbcTemplate.update("UPDATE bookings SET status = 'RETURNED' WHERE id = ?", BOOKING_ID);

        assertThatThrownBy(() -> rentalInspectionService.approveCondition(BOOKING_ID, renterJwt()))
                .isInstanceOf(AccessDeniedException.class);
        ConditionApprovalResponse approved = rentalInspectionService.approveCondition(BOOKING_ID, ownerJwt());

        assertThat(approved.bookingStatus()).isEqualTo(BookingStatus.INSPECTION_PASSED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, BOOKING_ID)).isEqualTo(BookingStatus.INSPECTION_PASSED.name());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT changed_by_user_id FROM booking_status_history
                WHERE booking_id = ? AND to_status = 'INSPECTION_PASSED'
                """, String.class, BOOKING_ID)).isEqualTo(OWNER_ID);
        assertThatThrownBy(() -> rentalInspectionService.approveCondition(BOOKING_ID, ownerJwt()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void bookingDetailsDriveTheLifecycleThroughAvailableActions() {
        when(privateObjectStorage.presignGet(anyString(), anyBoolean())).thenReturn("https://private.test/view");

        BookingDetailsResponse awaitingPayment = bookingDetailsService.get(BOOKING_ID, renterJwt(), List.of());
        assertThat(awaitingPayment.viewerRole()).isEqualTo("RENTER");
        assertThat(awaitingPayment.equipmentTitle()).isEqualTo("Phase four breaker");
        assertThat(awaitingPayment.availableActions()).containsExactly(BookingAction.PAY, BookingAction.CANCEL);
        assertThat(awaitingPayment.payment()).isNull();
        assertThat(bookingDetailsService.get(BOOKING_ID, ownerJwt(), List.of()).availableActions())
                .containsExactly(BookingAction.CANCEL);
        assertThatThrownBy(() -> bookingDetailsService.get(BOOKING_ID, adminJwt(), List.of()))
                .isInstanceOf(AccessDeniedException.class);

        paymentService.createOrder(BOOKING_ID, renterJwt());
        String captured = capturedPaymentPayload("pay_phase4");
        paymentService.handleWebhook("evt_details_capture",
                captured.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signatureVerifier.sign(captured, "test-webhook-secret"));
        BookingDetailsResponse confirmed = bookingDetailsService.get(BOOKING_ID, renterJwt(), List.of());
        assertThat(confirmed.payment().status()).isEqualTo("CAPTURED");
        assertThat(confirmed.availableActions()).containsExactly(
                BookingAction.GENERATE_HANDOVER_OTP, BookingAction.UPLOAD_HANDOVER_PHOTOS, BookingAction.CANCEL);

        OtpChallengeResponse ownerHandover = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, ownerJwt());
        OtpChallengeResponse renterHandover = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.HANDOVER, renterJwt());
        List<InspectionPhotoRequest> handoverPhotos = photos(InspectionType.HANDOVER);
        BookingDetailsResponse readyForHandover = bookingDetailsService.get(BOOKING_ID, ownerJwt(), List.of());
        assertThat(readyForHandover.otpStatus().ownerRequested()).isTrue();
        assertThat(readyForHandover.otpStatus().renterRequested()).isTrue();
        assertThat(readyForHandover.inspectionUploads()).hasSize(4)
                .allSatisfy(upload -> assertThat(upload.uploadedByMe()).isFalse());
        assertThat(readyForHandover.availableActions()).contains(BookingAction.SUBMIT_HANDOVER);
        rentalInspectionService.submit(BOOKING_ID, InspectionType.HANDOVER,
                inspection(ownerHandover.code(), renterHandover.code(), handoverPhotos), ownerJwt());

        jdbcTemplate.update("UPDATE bookings SET end_time = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), BOOKING_ID);
        OtpChallengeResponse ownerReturn = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, ownerJwt());
        OtpChallengeResponse renterReturn = rentalInspectionService.issueOtp(
                BOOKING_ID, InspectionType.RETURN, renterJwt());
        rentalInspectionService.submit(BOOKING_ID, InspectionType.RETURN,
                inspection(ownerReturn.code(), renterReturn.code(), photos(InspectionType.RETURN)), renterJwt());

        BookingDetailsResponse returned = bookingDetailsService.get(BOOKING_ID, ownerJwt(), List.of());
        assertThat(returned.viewerRole()).isEqualTo("OWNER");
        assertThat(returned.availableActions())
                .containsExactly(BookingAction.APPROVE_CONDITION, BookingAction.RAISE_DISPUTE);
        assertThat(returned.disputeDeadline()).isAfter(Instant.now().plusSeconds(23 * 60 * 60L));
        assertThat(returned.inspections()).hasSize(2)
                .allSatisfy(report -> assertThat(report.photos()).hasSize(4));
        assertThat(returned.otpStatus()).isNull();
        assertThat(bookingDetailsService.get(BOOKING_ID, renterJwt(), List.of()).availableActions()).isEmpty();

        String disputeId = rentalInspectionService.raiseDamageClaim(BOOKING_ID,
                new DamageClaimRequest("DAMAGE", new BigDecimal("5.00"), "Cracked housing."), ownerJwt());
        BookingDetailsResponse disputed = bookingDetailsService.get(
                BOOKING_ID, adminJwt(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(disputed.viewerRole()).isEqualTo("ADMIN");
        assertThat(disputed.dispute().disputeId()).isEqualTo(disputeId);
        assertThat(disputed.availableActions()).containsExactly(BookingAction.RESOLVE_DISPUTE);
        assertThat(bookingDetailsService.get(BOOKING_ID, ownerJwt(), List.of()).availableActions()).isEmpty();
    }

    @Test
    void adminDisputeQueueShowsDetailsWithInspectionPhotos() {
        when(privateObjectStorage.presignGet(anyString(), anyBoolean())).thenReturn("https://private.test/view");
        jdbcTemplate.update("UPDATE bookings SET status = 'RETURNED' WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO rental_inspections
                    (id, booking_id, inspection_type, inspector_user_id, checklist_json, otp_verified, inspected_at)
                VALUES ('phase4-return', ?, 'RETURN', ?, '{"condition_acceptable":false}', TRUE, ?)
                """, BOOKING_ID, RENTER_ID, java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        for (int index = 0; index < 4; index++) {
            jdbcTemplate.update("""
                    INSERT INTO rental_inspection_media (id, inspection_id, storage_object_key, media_tag, captured_at)
                    VALUES (?, 'phase4-return', ?, 'CONDITION', CURRENT_TIMESTAMP(6))
                    """, "phase4-media-" + index, "rentals/" + BOOKING_ID + "/return/photo-" + index + ".jpg");
        }
        String disputeId = rentalInspectionService.raiseDamageClaim(BOOKING_ID,
                new DamageClaimRequest("DAMAGE", new BigDecimal("12.50"), "Hydraulic leak."), ownerJwt());

        assertThat(adminDisputeService.list(DisputeStatus.OPEN))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.disputeId()).isEqualTo(disputeId);
                    assertThat(summary.bookingReference()).isEqualTo("BKG-PHASE4-001");
                    assertThat(summary.equipmentTitle()).isEqualTo("Phase four breaker");
                    assertThat(summary.raisedByName()).isEqualTo("Owner Test");
                });
        assertThat(adminDisputeService.list(DisputeStatus.RESOLVED)).isEmpty();
        assertThat(adminDisputeService.list(null)).hasSize(1);

        DisputeDetailResponse detail = adminDisputeService.get(disputeId);
        assertThat(detail.claimAmount()).isEqualByComparingTo("12.50");
        assertThat(detail.bookingStatus()).isEqualTo(BookingStatus.DISPUTED.name());
        assertThat(detail.renter().email()).isEqualTo("phase4-renter@example.com");
        assertThat(detail.inspections()).singleElement().satisfies(report -> {
            assertThat(report.checklist()).containsEntry("condition_acceptable", false);
            assertThat(report.photos()).hasSize(4)
                    .allSatisfy(photo -> assertThat(photo.url()).isEqualTo("https://private.test/view"));
        });
        verify(privateObjectStorage).presignGet("rentals/" + BOOKING_ID + "/return/photo-0.jpg", false);
        assertThatThrownBy(() -> adminDisputeService.get("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
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
