package com.marketplace.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.payment.PaymentService;
import com.marketplace.notification.NotificationEvent;
import com.marketplace.notification.NotificationType;
import com.marketplace.user.UserProfileService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cancels bookings before handover. Paid bookings are refunded through the idempotent refund path and their owner
 * settlement is re-targeted to the policy's owner compensation (or closed).
 */
@Service
public class CancellationService {

    static final Set<BookingStatus> UNPAID =
            Set.of(BookingStatus.REQUESTED, BookingStatus.PAYMENT_PENDING, BookingStatus.PAYMENT_FAILED);
    static final Set<BookingStatus> PAID_BEFORE_HANDOVER =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.HANDOVER_PENDING);
    static final Set<BookingStatus> RENTER_CANCELLABLE = Set.of(BookingStatus.REQUESTED,
            BookingStatus.PAYMENT_PENDING, BookingStatus.PAYMENT_FAILED, BookingStatus.CONFIRMED,
            BookingStatus.HANDOVER_PENDING);
    /** A {@code REQUESTED} booking is declined with the owner reject endpoint instead, without a penalty. */
    static final Set<BookingStatus> OWNER_CANCELLABLE = Set.of(BookingStatus.PAYMENT_PENDING,
            BookingStatus.PAYMENT_FAILED, BookingStatus.CONFIRMED, BookingStatus.HANDOVER_PENDING);

    private final JdbcTemplate jdbcTemplate;
    private final CancellationPolicy policy;
    private final PaymentService paymentService;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public CancellationService(
            JdbcTemplate jdbcTemplate,
            CancellationPolicy policy,
            PaymentService paymentService,
            UserProfileService userProfileService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.policy = policy;
        this.paymentService = paymentService;
        this.userProfileService = userProfileService;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    /** Dry run for the caller (renter or owner of the booking): what cancelling now would refund. */
    @Transactional(readOnly = true)
    public CancellationResponse quote(String bookingId, Jwt jwt) {
        CancellableBooking booking = findBooking(bookingId, false);
        String userId = userProfileService.findExisting(jwt.getSubject()).orElse(null);
        boolean owner;
        if (booking.renterUserId().equals(userId)) {
            owner = false;
        } else if (booking.ownerUserId().equals(userId)) {
            owner = true;
        } else {
            throw new AccessDeniedException("You do not have access to this booking.");
        }
        requireCancellable(booking, owner);
        return new CancellationResponse(bookingId, booking.status().name(), null, null,
                breakdown(booking, owner, Instant.now()));
    }

    @Transactional
    public CancellationResponse cancelByRenter(String bookingId, Jwt renterJwt, String reason) {
        CancellableBooking booking = findBooking(bookingId, true);
        String userId = userProfileService.requireExisting(renterJwt.getSubject());
        if (!booking.renterUserId().equals(userId)) {
            throw new AccessDeniedException("Only the renter who made the booking may cancel it.");
        }
        return cancel(booking, false, userId, reason);
    }

    @Transactional
    public CancellationResponse cancelByOwner(String bookingId, Jwt ownerJwt, String reason) {
        CancellableBooking booking = findBooking(bookingId, true);
        String userId = userProfileService.requireExisting(ownerJwt.getSubject());
        if (!booking.ownerUserId().equals(userId)) {
            throw new AccessDeniedException("Only the owner of the booked equipment may cancel it.");
        }
        return cancel(booking, true, userId, reason);
    }

    private CancellationResponse cancel(CancellableBooking booking, boolean owner, String actorUserId, String reason) {
        requireCancellable(booking, owner);
        Instant now = Instant.now();
        CancellationBreakdown breakdown = breakdown(booking, owner, now);
        boolean paid = PAID_BEFORE_HANDOVER.contains(booking.status());

        String refundId = null;
        if (paid) {
            if (breakdown.totalRefund().signum() > 0) {
                refundId = paymentService.refundForCancellation(booking.id(), breakdown.totalRefund());
            }
            paymentService.settleCancellation(booking.id(), breakdown.ownerCompensation());
        }

        BookingStatus target = owner ? BookingStatus.CANCELLED_BY_OWNER : BookingStatus.CANCELLED_BY_RENTER;
        jdbcTemplate.update("""
                UPDATE bookings SET status = ?, hold_expires_at = NULL WHERE id = ? AND status = ?
                """, target.name(), booking.id(), booking.status().name());
        jdbcTemplate.update("""
                INSERT INTO booking_status_history
                    (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), booking.id(), booking.status().name(), target.name(), actorUserId,
                reason);
        jdbcTemplate.update("""
                INSERT INTO booking_cancellations
                    (id, booking_id, cancelled_by_user_id, cancelled_by, reason, policy_tier, hours_before_start,
                     amount_paid, rental_charges, rental_refund, gateway_fee, deposit_refund, total_refund,
                     owner_compensation, platform_credit, razorpay_refund_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), booking.id(), actorUserId, breakdown.cancelledBy(), reason,
                breakdown.policyTier(), breakdown.hoursBeforeStart(), breakdown.amountPaid(),
                breakdown.rentalCharges(), breakdown.rentalRefund(), breakdown.gatewayFee(),
                breakdown.depositRefund(), breakdown.totalRefund(), breakdown.ownerCompensation(),
                breakdown.platformCredit(), refundId);

        if (owner && paid) {
            jdbcTemplate.update("""
                    UPDATE owner_profiles
                    SET cancellation_penalty_count = cancellation_penalty_count + 1,
                        last_cancellation_penalty_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """, booking.ownerProfileId());
            writeAudit(actorUserId, booking, breakdown, reason);
        }
        events.publishEvent(NotificationEvent.of(NotificationType.BOOKING_CANCELLED, booking.id(),
                Map.of("reason", reason, "breakdown", breakdown)));
        return new CancellationResponse(booking.id(), target.name(), now, refundId, breakdown);
    }

    private CancellationBreakdown breakdown(CancellableBooking booking, boolean owner, Instant now) {
        boolean paid = PAID_BEFORE_HANDOVER.contains(booking.status());
        if (owner) {
            return policy.forOwner(paid, booking.totalPayable(), booking.deposit(), booking.startTime(), now);
        }
        return policy.forRenter(paid, booking.totalPayable(), booking.deposit(), booking.expectedPayout(),
                booking.startTime(), now);
    }

    private static void requireCancellable(CancellableBooking booking, boolean owner) {
        Set<BookingStatus> allowed = owner ? OWNER_CANCELLABLE : RENTER_CANCELLABLE;
        if (!allowed.contains(booking.status())) {
            throw new InvalidStateTransitionException(booking.status().name(),
                    (owner ? BookingStatus.CANCELLED_BY_OWNER : BookingStatus.CANCELLED_BY_RENTER).name());
        }
    }

    private CancellableBooking findBooking(String bookingId, boolean lock) {
        CancellableBooking booking = jdbcTemplate.query("""
                        SELECT b.id, b.status, b.start_time, b.total_payable_amount, b.security_deposit_amount,
                               b.base_rental_amount, b.owner_commission_amount, b.renter_user_id,
                               op.user_id AS owner_user_id, op.id AS owner_profile_id
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        WHERE b.id = ?
                        """ + (lock ? " FOR UPDATE" : ""),
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    BigDecimal commission = resultSet.getBigDecimal("owner_commission_amount");
                    return new CancellableBooking(
                            resultSet.getString("id"),
                            BookingStatus.valueOf(resultSet.getString("status")),
                            resultSet.getTimestamp("start_time").toInstant(),
                            resultSet.getBigDecimal("total_payable_amount"),
                            resultSet.getBigDecimal("security_deposit_amount"),
                            resultSet.getBigDecimal("base_rental_amount")
                                    .subtract(commission == null ? BigDecimal.ZERO : commission),
                            resultSet.getString("renter_user_id"),
                            resultSet.getString("owner_user_id"),
                            resultSet.getString("owner_profile_id"));
                },
                bookingId);
        if (booking == null) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return booking;
    }

    private void writeAudit(String actorUserId, CancellableBooking booking, CancellationBreakdown breakdown,
                            String reason) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                    VALUES (?, ?, 'OWNER_CANCELLATION_PENALTY', 'owner_profiles', ?, ?)
                    """,
                    UUID.randomUUID().toString(), actorUserId, booking.ownerProfileId(),
                    objectMapper.writeValueAsString(Map.of(
                            "bookingId", booking.id(),
                            "renterPlatformCredit", breakdown.platformCredit(),
                            "renterRefund", breakdown.totalRefund(),
                            "reason", reason)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the owner cancellation audit entry.", exception);
        }
    }

    private record CancellableBooking(
            String id,
            BookingStatus status,
            Instant startTime,
            BigDecimal totalPayable,
            BigDecimal deposit,
            BigDecimal expectedPayout,
            String renterUserId,
            String ownerUserId,
            String ownerProfileId
    ) {
    }
}
