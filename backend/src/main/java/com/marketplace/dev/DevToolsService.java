package com.marketplace.dev;

import com.marketplace.booking.BookingService;
import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import com.marketplace.payment.PaymentService;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.review.ReviewService;
import com.marketplace.user.UserProfileService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shortcuts for local manual testing and the Playwright suite. Exists only under the dev and test profiles.
 */
@Service
@Profile({"dev", "test"})
public class DevToolsService {

    static final int MAX_SHIFT_HOURS = 24 * 365;

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final BookingService bookingService;
    private final RentalInspectionService inspectionService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;

    public DevToolsService(
            JdbcTemplate jdbcTemplate,
            UserProfileService userProfileService,
            KeycloakAdminClient keycloakAdminClient,
            BookingService bookingService,
            RentalInspectionService inspectionService,
            PaymentService paymentService,
            ReviewService reviewService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.bookingService = bookingService;
        this.inspectionService = inspectionService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
    }

    /**
     * Moves a booking through time: its start, end and hold times, plus everything dated relative to them (inspections,
     * status history, settlement release), shift by {@code hours}. A negative value makes the rental "happen" sooner.
     */
    @Transactional
    public ShiftTimeResponse shiftTime(String bookingId, int hours) {
        if (hours == 0 || Math.abs(hours) > MAX_SHIFT_HOURS) {
            throw new BadRequestException("Shift by a non-zero number of hours up to one year either way.");
        }
        int updated = jdbcTemplate.update("""
                UPDATE bookings
                SET start_time = TIMESTAMPADD(HOUR, ?, start_time),
                    end_time = TIMESTAMPADD(HOUR, ?, end_time),
                    hold_expires_at = CASE WHEN hold_expires_at IS NULL THEN NULL
                                           ELSE TIMESTAMPADD(HOUR, ?, hold_expires_at) END
                WHERE id = ?
                """, hours, hours, hours, bookingId);
        if (updated != 1) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        jdbcTemplate.update("UPDATE rental_inspections SET inspected_at = TIMESTAMPADD(HOUR, ?, inspected_at) WHERE booking_id = ?",
                hours, bookingId);
        jdbcTemplate.update("UPDATE booking_status_history SET created_at = TIMESTAMPADD(HOUR, ?, created_at) WHERE booking_id = ?",
                hours, bookingId);
        jdbcTemplate.update("""
                UPDATE owner_settlements SET release_after = TIMESTAMPADD(HOUR, ?, release_after)
                WHERE booking_id = ? AND release_after IS NOT NULL
                """, hours, bookingId);
        return jdbcTemplate.queryForObject("""
                        SELECT id, status, start_time, end_time, hold_expires_at FROM bookings WHERE id = ?
                        """,
                (rs, rowNum) -> new ShiftTimeResponse(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getTimestamp("start_time").toInstant(),
                        rs.getTimestamp("end_time").toInstant(),
                        rs.getTimestamp("hold_expires_at") == null ? null : rs.getTimestamp("hold_expires_at").toInstant()),
                bookingId);
    }

    /** Runs every scheduled sweep once, now, instead of waiting for the next minute. */
    public SweepResponse runScheduledJobs() {
        int expiredHolds = bookingService.expireHolds();
        int advancedRentals = inspectionService.advanceTimedStates();
        int releasedSettlements = paymentService.financialOperationsEnabled()
                ? paymentService.releaseMaturedSettlements()
                : 0;
        int closedBookings = reviewService.closeExpiredReviewWindows();
        return new SweepResponse(expiredHolds, advancedRentals, releasedSettlements, closedBookings);
    }

    /**
     * Makes the caller a verified owner (with a fake Razorpay payout account) and creates one approved listing in the
     * first active category. Each call adds a new listing, so tests can book without date clashes.
     */
    @Transactional
    public SeedResponse seedOwnerListing(Jwt jwt) {
        String userId = userProfileService.resolveOrCreate(jwt);
        List<String> profiles = jdbcTemplate.queryForList(
                "SELECT id FROM owner_profiles WHERE user_id = ?", String.class, userId);
        String ownerProfileId;
        if (profiles.isEmpty()) {
            ownerProfileId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO owner_profiles
                        (id, user_id, business_type, verification_status, verified_at, razorpay_linked_account_id)
                    VALUES (?, ?, 'INDIVIDUAL', 'VERIFIED', CURRENT_TIMESTAMP(6), ?)
                    """, ownerProfileId, userId, fakeAccountId());
        } else {
            ownerProfileId = profiles.get(0);
            jdbcTemplate.update("""
                    UPDATE owner_profiles
                    SET verification_status = 'VERIFIED',
                        verified_at = COALESCE(verified_at, CURRENT_TIMESTAMP(6)),
                        razorpay_linked_account_id = COALESCE(razorpay_linked_account_id, ?)
                    WHERE id = ?
                    """, fakeAccountId(), ownerProfileId);
        }

        List<String> categories = jdbcTemplate.queryForList(
                "SELECT id FROM equipment_categories WHERE is_active = TRUE ORDER BY name LIMIT 1", String.class);
        if (categories.isEmpty()) {
            throw new ConflictException("No active equipment category exists to list the seeded equipment in.");
        }
        String equipmentId = UUID.randomUUID().toString();
        String title = "Dev seed breaker " + Instant.now().toEpochMilli();
        jdbcTemplate.update("""
                INSERT INTO equipment
                    (id, owner_id, category_id, title, description, manufacturer, model_number, condition_grade,
                     specifications_json, base_daily_rate, base_weekly_rate, security_deposit, minimum_rental_days,
                     delivery_supported, pickup_supported, delivery_charge_flat, address_line, city, postal_code,
                     latitude, longitude, status)
                VALUES (?, ?, ?, ?, 'Seeded for local testing: 1500 W demolition hammer with chisel set.',
                        'Bosch', 'GSH 11 E', 'GOOD', '{"Power":"1500 W","Weight":"11 kg"}', 1000.00, 6000.00,
                        2000.00, 1, TRUE, TRUE, 300.00, 'Plot 1, Whitefield', 'Bengaluru', '560066',
                        12.9698000, 77.7499000, 'ACTIVE')
                """, equipmentId, ownerProfileId, categories.get(0), title);

        boolean ownerRoleGranted;
        try {
            keycloakAdminClient.grantRealmRole(jwt.getSubject(), "ROLE_OWNER");
            ownerRoleGranted = true;
        } catch (KeycloakAdminException exception) {
            ownerRoleGranted = false;
        }
        return new SeedResponse(ownerProfileId, equipmentId, title, ownerRoleGranted);
    }

    private static String fakeAccountId() {
        return "acc_FAKE" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public record ShiftTimeResponse(String bookingId, String status, Instant startTime, Instant endTime,
                                    Instant holdExpiresAt) {
    }

    public record SweepResponse(int expiredHolds, int advancedRentals, int releasedSettlements, int closedBookings) {
    }

    /** @param ownerRoleGranted false when Keycloak could not be reached; assign ROLE_OWNER by hand then */
    public record SeedResponse(String ownerProfileId, String equipmentId, String title, boolean ownerRoleGranted) {
    }
}
