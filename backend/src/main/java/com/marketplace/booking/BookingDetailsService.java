package com.marketplace.booking;

import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.payment.PaymentService;
import com.marketplace.rental.InspectionReport;
import com.marketplace.rental.InspectionReportReader;
import com.marketplace.rental.InspectionType;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.review.ReviewResponse;
import com.marketplace.review.ReviewService;
import com.marketplace.storage.PrivateObjectStorage;
import com.marketplace.user.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingDetailsService {

    private static final Logger log = LoggerFactory.getLogger(BookingDetailsService.class);

    private static final int MIN_INSPECTION_PHOTOS = 4;

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final InspectionReportReader inspectionReportReader;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final PrivateObjectStorage objectStorage;
    private final DeliveryService deliveryService;

    public BookingDetailsService(
            JdbcTemplate jdbcTemplate,
            UserProfileService userProfileService,
            InspectionReportReader inspectionReportReader,
            PaymentService paymentService,
            ReviewService reviewService,
            PrivateObjectStorage objectStorage,
            DeliveryService deliveryService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.inspectionReportReader = inspectionReportReader;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
        this.objectStorage = objectStorage;
        this.deliveryService = deliveryService;
    }

    @Transactional(readOnly = true)
    public BookingDetailsResponse get(String bookingId, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        BookingContext booking = findBooking(bookingId);
        String userId = userProfileService.findExisting(jwt.getSubject()).orElse(null);
        String viewerRole;
        if (userId != null && userId.equals(booking.renterUserId())) {
            viewerRole = "RENTER";
        } else if (userId != null && userId.equals(booking.ownerUserId())) {
            viewerRole = "OWNER";
        } else if (authorities.stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            viewerRole = "ADMIN";
        } else {
            throw new AccessDeniedException("You do not have access to this booking.");
        }

        BookingStatus status = BookingStatus.valueOf(booking.base().status());
        InspectionType activeInspection = activeInspection(status);
        List<InspectionReport> inspections = inspectionReportReader.forBooking(bookingId);
        List<BookingDetailsResponse.InspectionUpload> uploads = activeInspection == null
                ? List.of()
                : pendingUploads(bookingId, activeInspection, userId);
        BookingDetailsResponse.OtpStatus otpStatus = activeInspection == null
                ? null
                : otpStatus(bookingId, activeInspection, booking);
        BookingDetailsResponse.DisputeSummary dispute = latestDispute(bookingId);
        Instant disputeDeadline = inspections.stream()
                .filter(report -> InspectionType.RETURN.name().equals(report.type()))
                .map(InspectionReport::inspectedAt)
                .max(Instant::compareTo)
                .map(returnedAt -> returnedAt.plus(RentalInspectionService.DISPUTE_WINDOW))
                .orElse(null);

        boolean reviewable = status == BookingStatus.COMPLETED || status == BookingStatus.CLOSED;
        List<ReviewResponse> reviews = reviewable ? reviewService.listForBooking(bookingId) : List.of();
        boolean canReview = status == BookingStatus.COMPLETED
                && ("RENTER".equals(viewerRole) || "OWNER".equals(viewerRole))
                && !reviewService.hasReviewed(bookingId, userId)
                && reviewService.reviewWindowOpen(bookingId);

        List<BookingAction> actions = availableActions(viewerRole, status, booking, activeInspection,
                uploads.size(), otpStatus, dispute, disputeDeadline);
        if (canReview) {
            actions.add(BookingAction.REVIEW);
        }

        BookingResponse base = booking.base();
        return new BookingDetailsResponse(base.bookingId(), base.bookingReference(), base.equipmentId(),
                booking.equipmentTitle(), base.startTime(), base.endTime(), base.deliveryOption(), base.status(),
                base.holdExpiresAt(), base.pricing(), booking.ownerName(), booking.renterName(), viewerRole,
                paymentSummary(bookingId), inspections, uploads, otpStatus, dispute, disputeDeadline,
                status == BookingStatus.CANCELLED_BY_RENTER || status == BookingStatus.CANCELLED_BY_OWNER
                        ? cancellation(bookingId)
                        : null,
                reviews,
                actions,
                photos(base.equipmentId(), booking.ownerUserId(), booking.renterUserId()),
                deliveryService.address(bookingId),
                deliveryService.find(bookingId));
    }

    /**
     * The equipment's cover (whatever the listing's status now: the booking is the participants' own record) and the
     * two parties' profile photos. A storage hiccup leaves a photo out rather than failing the whole booking page.
     */
    private BookingDetailsResponse.Photos photos(String equipmentId, String ownerUserId, String renterUserId) {
        List<String> equipmentKeys = jdbcTemplate.queryForList("""
                        SELECT storage_object_key FROM equipment_images
                        WHERE equipment_id = ? AND uploaded_at IS NOT NULL
                        ORDER BY is_primary DESC, display_order, id
                        LIMIT 1
                        """, String.class, equipmentId);
        Map<String, String> profileKeys = new HashMap<>();
        jdbcTemplate.query("SELECT id, profile_photo_key FROM users WHERE id IN (?, ?)",
                (RowCallbackHandler) rs -> profileKeys.put(rs.getString("id"), rs.getString("profile_photo_key")),
                ownerUserId, renterUserId);
        return new BookingDetailsResponse.Photos(
                link(equipmentKeys.isEmpty() ? null : equipmentKeys.get(0)),
                link(profileKeys.get(ownerUserId)),
                link(profileKeys.get(renterUserId)));
    }

    private String link(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        try {
            return objectStorage.presignGet(objectKey, false);
        } catch (RuntimeException exception) {
            log.warn("Photo {} could not be linked: {}", objectKey, exception.getMessage());
            return null;
        }
    }

    private List<BookingAction> availableActions(
            String viewerRole,
            BookingStatus status,
            BookingContext booking,
            InspectionType activeInspection,
            int pendingUploads,
            BookingDetailsResponse.OtpStatus otpStatus,
            BookingDetailsResponse.DisputeSummary dispute,
            Instant disputeDeadline
    ) {
        Instant now = Instant.now();
        BookingResponse base = booking.base();
        boolean holdActive = base.holdExpiresAt() != null && base.holdExpiresAt().isAfter(now);
        boolean openDispute = dispute != null && "OPEN".equals(dispute.status());
        List<BookingAction> actions = new ArrayList<>();

        if ("RENTER".equals(viewerRole)
                && (status == BookingStatus.PAYMENT_PENDING || status == BookingStatus.PAYMENT_FAILED)
                && holdActive && paymentService.financialOperationsEnabled()) {
            actions.add(BookingAction.PAY);
        }
        if ("OWNER".equals(viewerRole) && status == BookingStatus.REQUESTED && holdActive && booking.ownerVerified()) {
            actions.add(BookingAction.ACCEPT);
            actions.add(BookingAction.REJECT);
        }
        if ("OWNER".equals(viewerRole) && DeliveryService.DISPATCHABLE.contains(status)
                && DeliveryOption.OWNER_DELIVERY.name().equals(base.deliveryOption())) {
            actions.add(BookingAction.MARK_OUT_FOR_DELIVERY);
        }
        if (("RENTER".equals(viewerRole) || "OWNER".equals(viewerRole)) && activeInspection != null) {
            Instant opensAt = activeInspection == InspectionType.HANDOVER ? base.startTime() : base.endTime();
            if (!opensAt.isAfter(now)) {
                boolean handover = activeInspection == InspectionType.HANDOVER;
                actions.add(handover ? BookingAction.GENERATE_HANDOVER_OTP : BookingAction.GENERATE_RETURN_OTP);
                actions.add(handover ? BookingAction.UPLOAD_HANDOVER_PHOTOS : BookingAction.UPLOAD_RETURN_PHOTOS);
                if (otpStatus.ownerRequested() && otpStatus.renterRequested()
                        && pendingUploads >= MIN_INSPECTION_PHOTOS) {
                    actions.add(handover ? BookingAction.SUBMIT_HANDOVER : BookingAction.SUBMIT_RETURN);
                }
            }
        }
        if ("OWNER".equals(viewerRole) && status == BookingStatus.RETURNED && !openDispute) {
            actions.add(BookingAction.APPROVE_CONDITION);
            if (disputeDeadline != null && now.isBefore(disputeDeadline)) {
                actions.add(BookingAction.RAISE_DISPUTE);
            }
        }
        if ("ADMIN".equals(viewerRole) && status == BookingStatus.DISPUTED && openDispute
                && paymentService.financialOperationsEnabled()) {
            actions.add(BookingAction.RESOLVE_DISPUTE);
        }
        // Cancelling a paid booking issues a refund, which needs payment operations enabled.
        boolean cancellationCanRefund = CancellationService.UNPAID.contains(status)
                || paymentService.financialOperationsEnabled();
        if (cancellationCanRefund
                && (("RENTER".equals(viewerRole) && CancellationService.RENTER_CANCELLABLE.contains(status))
                || ("OWNER".equals(viewerRole) && CancellationService.OWNER_CANCELLABLE.contains(status)))) {
            actions.add(BookingAction.CANCEL);
        }
        return actions;
    }

    private BookingDetailsResponse.Cancellation cancellation(String bookingId) {
        return jdbcTemplate.query("""
                        SELECT * FROM booking_cancellations WHERE booking_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? new BookingDetailsResponse.Cancellation(
                                resultSet.getString("reason"),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getString("razorpay_refund_id"),
                                new CancellationBreakdown(
                                        resultSet.getString("cancelled_by"),
                                        resultSet.getString("policy_tier"),
                                        resultSet.getBigDecimal("hours_before_start"),
                                        "INR",
                                        resultSet.getBigDecimal("amount_paid"),
                                        resultSet.getBigDecimal("rental_charges"),
                                        resultSet.getBigDecimal("rental_refund"),
                                        resultSet.getBigDecimal("gateway_fee"),
                                        resultSet.getBigDecimal("deposit_refund"),
                                        resultSet.getBigDecimal("total_refund"),
                                        resultSet.getBigDecimal("owner_compensation"),
                                        resultSet.getBigDecimal("platform_credit"),
                                        CancellationPolicy.NOTICE))
                        : null,
                bookingId);
    }

    private static InspectionType activeInspection(BookingStatus status) {
        return switch (status) {
            case CONFIRMED, HANDOVER_PENDING -> InspectionType.HANDOVER;
            case IN_PROGRESS, RETURN_PENDING -> InspectionType.RETURN;
            default -> null;
        };
    }

    private BookingContext findBooking(String bookingId) {
        BookingContext booking = jdbcTemplate.query("""
                        SELECT b.*, e.title AS equipment_title,
                               op.user_id AS owner_user_id, op.verification_status AS owner_verification_status,
                               ou.first_name AS owner_first_name, ou.last_name AS owner_last_name,
                               ru.first_name AS renter_first_name, ru.last_name AS renter_last_name
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN users ou ON ou.id = op.user_id
                        JOIN users ru ON ru.id = b.renter_user_id
                        WHERE b.id = ?
                        """,
                resultSet -> resultSet.next()
                        ? new BookingContext(
                                BookingService.mapBooking(resultSet),
                                resultSet.getString("renter_user_id"),
                                resultSet.getString("owner_user_id"),
                                "VERIFIED".equals(resultSet.getString("owner_verification_status")),
                                resultSet.getString("equipment_title"),
                                resultSet.getString("owner_first_name") + " " + resultSet.getString("owner_last_name"),
                                resultSet.getString("renter_first_name") + " " + resultSet.getString("renter_last_name"))
                        : null,
                bookingId);
        if (booking == null) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return booking;
    }

    private BookingDetailsResponse.PaymentSummary paymentSummary(String bookingId) {
        List<BookingDetailsResponse.Refund> refunds = jdbcTemplate.query("""
                        SELECT refund_type, amount, status, created_at FROM refunds
                        WHERE booking_id = ? ORDER BY created_at
                        """,
                (resultSet, rowNumber) -> new BookingDetailsResponse.Refund(
                        resultSet.getString("refund_type"),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant()),
                bookingId);
        return jdbcTemplate.query("""
                        SELECT id, status, amount, currency, razorpay_order_id, failure_reason
                        FROM payments WHERE booking_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? new BookingDetailsResponse.PaymentSummary(
                                resultSet.getString("id"),
                                resultSet.getString("status"),
                                resultSet.getBigDecimal("amount"),
                                resultSet.getString("currency"),
                                resultSet.getString("razorpay_order_id"),
                                resultSet.getString("failure_reason"),
                                refunds)
                        : null,
                bookingId);
    }

    private List<BookingDetailsResponse.InspectionUpload> pendingUploads(
            String bookingId,
            InspectionType type,
            String viewerUserId
    ) {
        return jdbcTemplate.query("""
                        SELECT inspection_type, storage_object_key, uploaded_by_user_id, expires_at
                        FROM rental_inspection_uploads
                        WHERE booking_id = ? AND inspection_type = ? AND attached_at IS NULL AND expires_at > ?
                        ORDER BY created_at
                        """,
                (resultSet, rowNumber) -> new BookingDetailsResponse.InspectionUpload(
                        resultSet.getString("inspection_type"),
                        resultSet.getString("storage_object_key"),
                        resultSet.getString("uploaded_by_user_id").equals(viewerUserId),
                        resultSet.getTimestamp("expires_at").toInstant()),
                bookingId, type.name(), Timestamp.from(Instant.now()));
    }

    private BookingDetailsResponse.OtpStatus otpStatus(String bookingId, InspectionType type, BookingContext booking) {
        Instant[] expiry = new Instant[2];
        jdbcTemplate.query("""
                        SELECT participant_user_id, expires_at
                        FROM rental_inspection_otps
                        WHERE booking_id = ? AND inspection_type = ? AND verified_at IS NULL AND expires_at > ?
                        """,
                resultSet -> {
                    String participant = resultSet.getString("participant_user_id");
                    Instant expiresAt = resultSet.getTimestamp("expires_at").toInstant();
                    if (participant.equals(booking.ownerUserId())) {
                        expiry[0] = expiresAt;
                    } else if (participant.equals(booking.renterUserId())) {
                        expiry[1] = expiresAt;
                    }
                },
                bookingId, type.name(), Timestamp.from(Instant.now()));
        return new BookingDetailsResponse.OtpStatus(type.name(), expiry[0] != null, expiry[0],
                expiry[1] != null, expiry[1]);
    }

    private BookingDetailsResponse.DisputeSummary latestDispute(String bookingId) {
        return jdbcTemplate.query("""
                        SELECT id, status, dispute_category, claim_amount, approved_deduction_amount,
                               description, resolution_notes, created_at, resolved_at
                        FROM disputes WHERE booking_id = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    Timestamp resolvedAt = resultSet.getTimestamp("resolved_at");
                    return new BookingDetailsResponse.DisputeSummary(
                            resultSet.getString("id"),
                            resultSet.getString("status"),
                            resultSet.getString("dispute_category"),
                            resultSet.getBigDecimal("claim_amount"),
                            resultSet.getBigDecimal("approved_deduction_amount"),
                            resultSet.getString("description"),
                            resultSet.getString("resolution_notes"),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resolvedAt == null ? null : resolvedAt.toInstant());
                },
                bookingId);
    }

    private record BookingContext(
            BookingResponse base,
            String renterUserId,
            String ownerUserId,
            boolean ownerVerified,
            String equipmentTitle,
            String ownerName,
            String renterName
    ) {
    }
}
