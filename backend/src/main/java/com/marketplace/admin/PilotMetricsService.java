package com.marketplace.admin;

import com.marketplace.exception.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class PilotMetricsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int MAX_RANGE_DAYS = 366;

    private final JdbcTemplate jdbcTemplate;

    public PilotMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PilotMetricsResponse getMetrics(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) {
            throw new ConflictException("The metrics start date must not be after the end date.");
        }
        if (Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays() > MAX_RANGE_DAYS) {
            throw new ConflictException("The metrics date range cannot exceed 366 days.");
        }

        Timestamp start = Timestamp.from(from.atStartOfDay().toInstant(ZoneOffset.UTC));
        Timestamp endExclusive = Timestamp.from(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));

        long bookingRequests = scalarLong("""
                SELECT COUNT(*) FROM bookings
                WHERE created_at >= ? AND created_at < ?
                """, start, endExclusive);
        long capturedBookings = scalarLong("""
                SELECT COUNT(DISTINCT b.id)
                FROM bookings b
                JOIN payments p ON p.booking_id = b.id
                WHERE b.created_at >= ? AND b.created_at < ? AND p.status = 'CAPTURED'
                """, start, endExclusive);
        BigDecimal capturedGrossAmount = scalarAmount("""
                SELECT COALESCE(SUM(p.amount), 0)
                FROM bookings b
                JOIN payments p ON p.booking_id = b.id
                WHERE b.created_at >= ? AND b.created_at < ? AND p.status = 'CAPTURED'
                """, start, endExclusive);

        List<Long> ownerResponseSeconds = jdbcTemplate.query("""
                        SELECT b.created_at, response.responded_at
                        FROM bookings b
                        JOIN (
                            SELECT booking_id, MIN(created_at) AS responded_at
                            FROM booking_status_history
                            WHERE to_status IN ('PAYMENT_PENDING', 'OWNER_REJECTED')
                            GROUP BY booking_id
                        ) response ON response.booking_id = b.id
                        WHERE b.created_at >= ? AND b.created_at < ?
                          AND response.responded_at >= b.created_at
                        """,
                (rs, rowNum) -> Duration.between(
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("responded_at").toInstant()).getSeconds(),
                start, endExclusive);
        BigDecimal averageOwnerResponseMinutes = ownerResponseSeconds.isEmpty() ? null :
                BigDecimal.valueOf(ownerResponseSeconds.stream().mapToLong(Long::longValue).average().orElseThrow())
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        long disputes = scalarLong("""
                SELECT COUNT(*) FROM disputes WHERE created_at >= ? AND created_at < ?
                """, start, endExclusive);
        long openDisputes = scalarLong("""
                SELECT COUNT(*) FROM disputes WHERE status = 'OPEN'
                """);
        BigDecimal rentalGrossAmount = scalarAmount("""
                SELECT COALESCE(SUM(gross_rental_amount), 0)
                FROM owner_settlements WHERE created_at >= ? AND created_at < ?
                """, start, endExclusive);
        BigDecimal platformCommissionAmount = scalarAmount("""
                SELECT COALESCE(SUM(platform_commission_amount), 0)
                FROM owner_settlements WHERE created_at >= ? AND created_at < ?
                """, start, endExclusive);
        long verifiedOwners = scalarLong("""
                SELECT COUNT(*) FROM owner_profiles WHERE verification_status = 'VERIFIED'
                """);
        long activeListings = scalarLong("""
                SELECT COUNT(*) FROM equipment WHERE status = 'ACTIVE'
                """);
        long pendingSettlements = scalarLong("""
                SELECT COUNT(*) FROM owner_settlements WHERE status = 'PENDING'
                """);
        long releasedSettlements = scalarLong("""
                SELECT COUNT(*) FROM owner_settlements
                WHERE status = 'RELEASED' AND released_at >= ? AND released_at < ?
                """, start, endExclusive);

        return new PilotMetricsResponse(
                from,
                to,
                bookingRequests,
                capturedBookings,
                percentage(capturedBookings, bookingRequests),
                capturedGrossAmount,
                rentalGrossAmount,
                platformCommissionAmount,
                percentage(platformCommissionAmount, rentalGrossAmount),
                averageOwnerResponseMinutes,
                disputes,
                openDisputes,
                verifiedOwners,
                activeListings,
                pendingSettlements,
                releasedSettlements);
    }

    private long scalarLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private BigDecimal scalarAmount(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? ZERO : value;
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
