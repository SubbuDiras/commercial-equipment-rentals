package com.rental.Marketplace;

import com.marketplace.booking.BookingCreateRequest;
import com.marketplace.booking.BookingPricing;
import com.marketplace.booking.BookingResponse;
import com.marketplace.booking.BookingService;
import com.marketplace.booking.BookingStatus;
import com.marketplace.booking.DeliveryOption;
import com.marketplace.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@ActiveProfiles("test")
class BookingServiceTests {

    private static final String OWNER_SUBJECT = "booking-owner";
    private static final String OWNER_USER_ID = "booking-owner-user";
    private static final String OWNER_PROFILE_ID = "booking-owner-profile";
    private static final String EQUIPMENT_ID = "booking-equipment";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @BeforeEach
    void createBookingSchema() {
        jdbcTemplate.execute("SET LOCK_TIMEOUT 15000");
        jdbcTemplate.execute("DROP TABLE IF EXISTS booking_price_items");
        jdbcTemplate.execute("DROP TABLE IF EXISTS booking_status_history");
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
                    verification_status VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment_categories (
                    id CHAR(36) PRIMARY KEY,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment (
                    id CHAR(36) PRIMARY KEY,
                    owner_id CHAR(36) NOT NULL,
                    category_id CHAR(36) NOT NULL,
                    base_daily_rate DECIMAL(12,2) NOT NULL,
                    base_weekly_rate DECIMAL(12,2),
                    security_deposit DECIMAL(12,2) NOT NULL,
                    minimum_rental_days INT NOT NULL,
                    delivery_supported BOOLEAN NOT NULL,
                    pickup_supported BOOLEAN NOT NULL,
                    delivery_charge_flat DECIMAL(12,2),
                    status VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE bookings (
                    id CHAR(36) PRIMARY KEY,
                    booking_reference VARCHAR(32) NOT NULL UNIQUE,
                    renter_user_id CHAR(36) NOT NULL,
                    equipment_id CHAR(36) NOT NULL,
                    start_time TIMESTAMP(6) NOT NULL,
                    end_time TIMESTAMP(6) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    hold_expires_at TIMESTAMP(6),
                    delivery_option VARCHAR(30) NOT NULL,
                    delivery_address_json CLOB,
                    currency VARCHAR(10) NOT NULL,
                    base_rental_amount DECIMAL(12,2) NOT NULL,
                    delivery_charge DECIMAL(12,2) NOT NULL,
                    security_deposit_amount DECIMAL(12,2) NOT NULL,
                    platform_fee_amount DECIMAL(12,2) NOT NULL,
                    owner_commission_amount DECIMAL(12,2),
                    tax_amount DECIMAL(12,2) NOT NULL,
                    total_payable_amount DECIMAL(12,2) NOT NULL,
                    owner_earnings_estimated DECIMAL(12,2) NOT NULL,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
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
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE booking_price_items (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    item_type VARCHAR(50) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);

        jdbcTemplate.update("""
                INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """, OWNER_USER_ID, OWNER_SUBJECT, "booking-owner@example.com", "9876543210", "Owner", "Test");
        jdbcTemplate.update("""
                INSERT INTO owner_profiles (id, user_id, verification_status)
                VALUES (?, ?, 'VERIFIED')
                """, OWNER_PROFILE_ID, OWNER_USER_ID);
        jdbcTemplate.update("INSERT INTO equipment_categories (id, is_active) VALUES ('booking-category', TRUE)");
        jdbcTemplate.update("""
                INSERT INTO equipment
                    (id, owner_id, category_id, base_daily_rate, base_weekly_rate, security_deposit,
                     minimum_rental_days, delivery_supported, pickup_supported, delivery_charge_flat, status)
                VALUES (?, ?, 'booking-category', 1200.00, 7000.00, 5000.00, 1, TRUE, TRUE, 350.00, 'ACTIVE')
                """, EQUIPMENT_ID, OWNER_PROFILE_ID);
    }

    @Test
    void persistsImmutablePriceSnapshotAndReturnsBookingHistory() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse created = bookingService.create(renterJwt("snapshot-renter"), request(start, 4));

        assertThat(created.status()).isEqualTo(BookingStatus.REQUESTED.name());
        assertThat(created.pricing()).usingRecursiveComparison().isEqualTo(
                new BookingPricing("INR", 4, new BigDecimal("4800.00"), BigDecimal.ZERO.setScale(2),
                        new BigDecimal("144.00"), new BigDecimal("889.92"),
                        new BigDecimal("5000.00"), new BigDecimal("10833.92")));
        assertThat(created.holdExpiresAt()).isAfter(Instant.now().plusSeconds(3 * 60 * 60));

        jdbcTemplate.update("UPDATE equipment SET base_daily_rate = 9999.00, security_deposit = 1.00 WHERE id = ?",
                EQUIPMENT_ID);
        BookingResponse persisted = bookingService.get(created.bookingId(), renterJwt("snapshot-renter"), List.of());

        assertThat(persisted.pricing().baseRentalAmount()).isEqualByComparingTo("4800.00");
        assertThat(persisted.pricing().securityDepositAmount()).isEqualByComparingTo("5000.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_commission_amount FROM bookings WHERE id = ?",
                BigDecimal.class, created.bookingId())).isEqualByComparingTo("576.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_earnings_estimated FROM bookings WHERE id = ?",
                BigDecimal.class, created.bookingId())).isEqualByComparingTo("4224.00");
        assertThat(bookingService.listForRenter(renterJwt("snapshot-renter"), 0, 20))
                .extracting(BookingResponse::bookingId)
                .containsExactly(created.bookingId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_price_items WHERE booking_id = ?",
                Integer.class, created.bookingId())).isEqualTo(6);
    }

    @Test
    void ownerCanAcceptAndRejectOwnRequestsWithImmutableHistory() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse acceptedRequest = bookingService.create(renterJwt("accept-renter"), request(start, 2));
        BookingResponse accepted = bookingService.accept(acceptedRequest.bookingId(), ownerJwt());

        assertThat(accepted.status()).isEqualTo(BookingStatus.PAYMENT_PENDING.name());
        assertThat(accepted.holdExpiresAt()).isAfter(Instant.now().plusSeconds(29 * 60));

        BookingResponse rejectedRequest = bookingService.create(
                renterJwt("reject-renter"), request(start.plusSeconds(5 * 86_400L), 2));
        BookingResponse rejected = bookingService.reject(
                rejectedRequest.bookingId(), ownerJwt(), "Equipment unavailable.");
        assertThat(rejected.status()).isEqualTo(BookingStatus.OWNER_REJECTED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason_or_notes FROM booking_status_history WHERE booking_id = ? AND to_status = ?",
                String.class, rejected.bookingId(), BookingStatus.OWNER_REJECTED.name()))
                .isEqualTo("Equipment unavailable.");
    }

    @Test
    void expiringHoldsReleasesTheDatesForAnotherBooking() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse first = bookingService.create(renterJwt("expiry-renter"), request(start, 2));
        jdbcTemplate.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), first.bookingId());

        assertThat(bookingService.expireHolds()).isEqualTo(1);
        assertThat(bookingService.get(first.bookingId(), renterJwt("expiry-renter"), List.of()).status())
                .isEqualTo(BookingStatus.REQUEST_EXPIRED.name());

        BookingResponse second = bookingService.create(renterJwt("replacement-renter"), request(start, 2));
        assertThat(second.status()).isEqualTo(BookingStatus.REQUESTED.name());
    }

    @Test
    void failedPaymentRemainsBlockedUntilThePaymentHoldExpires() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse booking = bookingService.create(renterJwt("payment-failed-renter"), request(start, 2));
        jdbcTemplate.update("UPDATE bookings SET status = 'PAYMENT_FAILED', hold_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), booking.bookingId());

        assertThat(bookingService.expireHolds()).isEqualTo(1);
        assertThat(bookingService.get(booking.bookingId(), renterJwt("payment-failed-renter"), List.of()).status())
                .isEqualTo(BookingStatus.HOLD_EXPIRED.name());
    }

    @Test
    void expiredOwnerReviewHoldStopsBlockingAvailabilityBeforeTheExpirySweepRuns() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse booking = bookingService.create(renterJwt("availability-hold-renter"), request(start, 2));
        jdbcTemplate.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), booking.bookingId());

        assertThat(bookingService.isAvailable(EQUIPMENT_ID, start, start.plusSeconds(86_400L))).isTrue();
        assertThat(bookingService.expireHolds()).isEqualTo(1);
        assertThat(bookingService.get(booking.bookingId(), renterJwt("availability-hold-renter"), List.of()).status())
                .isEqualTo(BookingStatus.REQUEST_EXPIRED.name());
    }

    @Test
    void acceptedBookingPaymentHoldExpiresAfterThirtyMinutesAndReleasesInventory() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        BookingResponse request = bookingService.create(renterJwt("accepted-expiry-renter"), request(start, 2));
        BookingResponse accepted = bookingService.accept(request.bookingId(), ownerJwt());
        assertThat(bookingService.isAvailable(EQUIPMENT_ID, start, start.plusSeconds(86_400L))).isFalse();
        jdbcTemplate.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), accepted.bookingId());

        assertThat(bookingService.expireHolds()).isEqualTo(1);
        assertThat(bookingService.get(accepted.bookingId(), renterJwt("accepted-expiry-renter"), List.of()).status())
                .isEqualTo(BookingStatus.HOLD_EXPIRED.name());
        assertThat(bookingService.isAvailable(EQUIPMENT_ID, start, start.plusSeconds(86_400L))).isTrue();
    }

    @Test
    void tenConcurrentOverlappingRequestsCreateExactlyOneReservation() throws Exception {
        int attempts = 10;
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> attemptsResult = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                String subject = "concurrent-renter-" + i;
                attemptsResult.add(executor.submit(() -> {
                    ready.countDown();
                    startTogether.await();
                    try {
                        bookingService.create(renterJwt(subject), request(start, 2));
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                }));
            }
            ready.await();
            startTogether.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<Boolean> result : attemptsResult) {
                if (result.get()) {
                    created++;
                } else {
                    conflicts++;
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(9);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bookings WHERE equipment_id = ?",
                    Integer.class, EQUIPMENT_ID)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsOverlappingAvailabilityAndAllowsAdjacentPeriods() {
        Instant start = Instant.now().plusSeconds(172_800).truncatedTo(ChronoUnit.SECONDS);
        bookingService.create(renterJwt("first-period"), request(start, 2));

        assertThat(bookingService.isAvailable(EQUIPMENT_ID, start.plusSeconds(86_400), start.plusSeconds(3 * 86_400L)))
                .isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> bookingService.create(renterJwt("overlapping-period"), request(start.plusSeconds(86_400), 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlapping");

        assertThat(bookingService.isAvailable(EQUIPMENT_ID, start.plusSeconds(2 * 86_400L),
                start.plusSeconds(3 * 86_400L))).isTrue();
        BookingResponse adjacent = bookingService.create(
                renterJwt("adjacent-period"), request(start.plusSeconds(2 * 86_400L), 1));
        assertThat(adjacent.status()).isEqualTo(BookingStatus.REQUESTED.name());
    }

    private static BookingCreateRequest request(Instant start, int rentalDays) {
        return new BookingCreateRequest(
                EQUIPMENT_ID,
                start,
                start.plusSeconds(rentalDays * 86_400L),
                DeliveryOption.PICKUP,
                null);
    }

    private static Jwt renterJwt(String subject) {
        return Jwt.withTokenValue(UUID.randomUUID().toString())
                .header("alg", "none")
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("preferred_username", subject)
                .build();
    }

    private static Jwt ownerJwt() {
        return Jwt.withTokenValue("owner-token")
                .header("alg", "none")
                .subject(OWNER_SUBJECT)
                .claim("email", "booking-owner@example.com")
                .build();
    }
}
