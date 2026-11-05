package com.marketplace.review;

import com.marketplace.booking.BookingStatus;
import com.marketplace.common.PageResponse;
import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.user.UserProfileService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Two-sided reviews after a completed rental. The renter reviews the owner and the equipment; the owner reviews
 * the renter. The booking becomes {@code CLOSED} when both have reviewed or the review window lapses.
 */
@Service
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewService {

    /** When the booking became COMPLETED; falls back to the last update for rows without status history. */
    private static final String COMPLETED_AT = """
            COALESCE((SELECT MAX(h.created_at) FROM booking_status_history h
                      WHERE h.booking_id = b.id AND h.to_status = 'COMPLETED'), b.updated_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final ReviewProperties properties;

    public ReviewService(JdbcTemplate jdbcTemplate, UserProfileService userProfileService, ReviewProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.properties = properties;
    }

    @Transactional
    public ReviewResponse submit(String bookingId, Jwt jwt, ReviewRequest request) {
        ReviewableBooking booking = lockBooking(bookingId);
        String userId = userProfileService.requireExisting(jwt.getSubject());
        boolean renter = userId.equals(booking.renterUserId());
        if (!renter && !userId.equals(booking.ownerUserId())) {
            throw new AccessDeniedException("Only the renter or the equipment owner may review this booking.");
        }
        if (booking.status() != BookingStatus.COMPLETED) {
            throw new InvalidStateTransitionException(booking.status().name(), "REVIEWED");
        }
        if (Instant.now().isAfter(windowClosesAt(booking.completedAt()))) {
            throw new ConflictException("The review window for this booking has closed.");
        }
        if (hasReviewed(bookingId, userId)) {
            throw new ConflictException("You have already reviewed this booking.");
        }

        String reviewId = UUID.randomUUID().toString();
        String comments = request.comments() == null || request.comments().isBlank() ? null : request.comments().trim();
        try {
            jdbcTemplate.update("""
                    INSERT INTO reviews
                        (id, booking_id, reviewer_user_id, reviewee_user_id, equipment_id, rating, comments)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    reviewId, bookingId, userId, renter ? booking.ownerUserId() : booking.renterUserId(),
                    booking.equipmentId(), request.rating(), comments);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("You have already reviewed this booking.");
        }

        BookingStatus status = BookingStatus.COMPLETED;
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE booking_id = ?", Integer.class, bookingId);
        if (reviewCount != null && reviewCount >= 2) {
            close(bookingId, userId, "Both parties reviewed the rental.");
            status = BookingStatus.CLOSED;
        }
        return listForBooking(bookingId).stream()
                .filter(review -> review.reviewId().equals(reviewId))
                .findFirst()
                .orElseThrow()
                .withBookingStatus(status.name());
    }

    /** Reviews on one booking, for its participants. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listForBooking(String bookingId) {
        return jdbcTemplate.query("""
                        SELECT r.id, r.booking_id, r.rating, r.comments, r.created_at,
                               CASE WHEN r.reviewer_user_id = b.renter_user_id THEN 'RENTER' ELSE 'OWNER' END
                                   AS reviewer_role,
                               u.first_name, u.last_name
                        FROM reviews r
                        JOIN bookings b ON b.id = r.booking_id
                        JOIN users u ON u.id = r.reviewer_user_id
                        WHERE r.booking_id = ?
                        ORDER BY r.created_at
                        """,
                (resultSet, rowNumber) -> new ReviewResponse(
                        resultSet.getString("id"),
                        resultSet.getString("booking_id"),
                        resultSet.getString("reviewer_role"),
                        displayName(resultSet.getString("first_name"), resultSet.getString("last_name")),
                        resultSet.getInt("rating"),
                        resultSet.getString("comments"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        null),
                bookingId);
    }

    public boolean hasReviewed(String bookingId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE booking_id = ? AND reviewer_user_id = ?",
                Integer.class, bookingId, userId);
        return count != null && count > 0;
    }

    /** Whether a {@code COMPLETED} booking can still be reviewed. */
    public boolean reviewWindowOpen(String bookingId) {
        Timestamp completedAt = jdbcTemplate.queryForObject(
                "SELECT " + COMPLETED_AT + " FROM bookings b WHERE b.id = ?", Timestamp.class, bookingId);
        return completedAt != null && !Instant.now().isAfter(windowClosesAt(completedAt.toInstant()));
    }

    /** Renters' published reviews of a publicly listed piece of equipment, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<EquipmentReviewResponse> equipmentReviews(String equipmentId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        Integer listed = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.id = ? AND e.status = 'ACTIVE' AND op.verification_status = 'VERIFIED'
                          AND c.is_active = TRUE
                        """,
                Integer.class, equipmentId);
        if (listed == null || listed == 0) {
            throw new ResourceNotFoundException("Active equipment listing not found.");
        }
        String from = """
                FROM reviews r
                JOIN bookings b ON b.id = r.booking_id
                JOIN users u ON u.id = r.reviewer_user_id
                WHERE r.equipment_id = ? AND r.reviewer_user_id = b.renter_user_id AND r.is_published = TRUE
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class, equipmentId);
        List<EquipmentReviewResponse> content = jdbcTemplate.query(
                "SELECT r.id, r.rating, r.comments, r.created_at, u.first_name, u.last_name "
                        + from + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> new EquipmentReviewResponse(
                        resultSet.getString("id"),
                        resultSet.getInt("rating"),
                        resultSet.getString("comments"),
                        displayName(resultSet.getString("first_name"), resultSet.getString("last_name")),
                        resultSet.getTimestamp("created_at").toInstant()),
                equipmentId, size, (long) page * size);
        return PageResponse.of(content, page, size, total == null ? 0 : total);
    }

    /** Closes completed bookings whose review window has lapsed. Called by the scheduled rental sweep. */
    @Transactional
    public int closeExpiredReviewWindows() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.windowDays()));
        List<String> expired = jdbcTemplate.queryForList("""
                        SELECT b.id FROM bookings b
                        WHERE b.status = 'COMPLETED' AND %s <= ?
                        ORDER BY b.id
                        LIMIT 100
                        FOR UPDATE
                        """.formatted(COMPLETED_AT),
                String.class, Timestamp.from(cutoff));
        int closed = 0;
        for (String bookingId : expired) {
            if (close(bookingId, null, "Review window of " + properties.windowDays() + " days elapsed.")) {
                closed++;
            }
        }
        return closed;
    }

    private boolean close(String bookingId, String actorUserId, String reason) {
        int updated = jdbcTemplate.update(
                "UPDATE bookings SET status = 'CLOSED' WHERE id = ? AND status = 'COMPLETED'", bookingId);
        if (updated == 1) {
            jdbcTemplate.update("""
                    INSERT INTO booking_status_history
                        (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                    VALUES (?, ?, 'COMPLETED', 'CLOSED', ?, ?)
                    """,
                    UUID.randomUUID().toString(), bookingId, actorUserId, reason);
        }
        return updated == 1;
    }

    private Instant windowClosesAt(Instant completedAt) {
        return completedAt.plus(Duration.ofDays(properties.windowDays()));
    }

    private ReviewableBooking lockBooking(String bookingId) {
        ReviewableBooking booking = jdbcTemplate.query("""
                        SELECT b.id, b.status, b.renter_user_id, b.equipment_id, op.user_id AS owner_user_id,
                               %s AS completed_at
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        WHERE b.id = ?
                        FOR UPDATE
                        """.formatted(COMPLETED_AT),
                resultSet -> resultSet.next()
                        ? new ReviewableBooking(
                                resultSet.getString("id"),
                                BookingStatus.valueOf(resultSet.getString("status")),
                                resultSet.getString("renter_user_id"),
                                resultSet.getString("owner_user_id"),
                                resultSet.getString("equipment_id"),
                                resultSet.getTimestamp("completed_at") == null
                                        ? Instant.now()
                                        : resultSet.getTimestamp("completed_at").toInstant())
                        : null,
                bookingId);
        if (booking == null) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return booking;
    }

    private static String displayName(String firstName, String lastName) {
        return lastName == null || lastName.isBlank()
                ? firstName
                : firstName + " " + lastName.charAt(0) + ".";
    }

    private record ReviewableBooking(
            String id,
            BookingStatus status,
            String renterUserId,
            String ownerUserId,
            String equipmentId,
            Instant completedAt
    ) {
    }
}
