package com.rental.Marketplace;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.marketplace.booking.BookingCreateRequest;
import com.marketplace.booking.BookingService;
import com.marketplace.booking.DeliveryOption;
import com.marketplace.exception.ConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = com.marketplace.MarketplaceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTests {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.0")
                    .withDatabaseName("marketplace_test")
                    .withUsername("marketplace_test")
                    .withPassword("marketplace_test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void appliesAllMigrationsToAnEmptyMysqlDatabase() {
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND ((table_name = 'bookings' AND column_name = 'owner_commission_amount')
                    OR (table_name = 'owner_verification_documents' AND column_name = 'upload_expires_at')
                    OR (table_name = 'owner_settlements' AND column_name IN ('release_after', 'owner_damage_award_amount'))
                    OR (table_name = 'equipment_images' AND column_name IN ('content_type', 'uploaded_at', 'upload_expires_at')))
                """, Integer.class)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND ((table_name = 'refunds' AND index_name = 'uk_refund_booking_type')
                    OR (table_name = 'refunds' AND index_name = 'uk_razorpay_refund'))
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_categories WHERE is_active = TRUE",
                Integer.class)).isEqualTo(6);
    }

    @Test
    void concurrentOverlappingBookingRequestsReserveOnlyOneEquipmentUnit() throws Exception {
        String ownerUserId = UUID.randomUUID().toString();
        String ownerProfileId = UUID.randomUUID().toString();
        String categoryId = UUID.randomUUID().toString();
        String equipmentId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ownerUserId, UUID.randomUUID().toString(), "mysql-owner@example.com",
                "9876543210", "Test", "Owner");
        jdbcTemplate.update("""
                INSERT INTO owner_profiles (id, user_id, business_type, verification_status)
                VALUES (?, ?, 'INDIVIDUAL', 'VERIFIED')
                """, ownerProfileId, ownerUserId);
        jdbcTemplate.update("""
                INSERT INTO equipment_categories (id, slug, name)
                VALUES (?, ?, ?)
                """, categoryId, UUID.randomUUID().toString(), "Test equipment");
        jdbcTemplate.update("""
                INSERT INTO equipment
                    (id, owner_id, category_id, title, description, manufacturer, model_number,
                     base_daily_rate, security_deposit, minimum_rental_days, delivery_supported,
                     pickup_supported, delivery_charge_flat, address_line, city, postal_code,
                     latitude, longitude, status)
                VALUES (?, ?, ?, 'Test listing', 'Test listing', 'Test', 'TEST-1',
                        1200.00, 5000.00, 1, FALSE, TRUE, 0.00, 'Test address',
                        'Bengaluru', '560001', 12.9716, 77.5946, 'ACTIVE')
                """, equipmentId, ownerProfileId, categoryId);

        int requestCount = 10;
        Instant start = Instant.now().plusSeconds(172_800);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                String subject = "mysql-renter-" + index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    startTogether.await();
                    try {
                        Jwt jwt = Jwt.withTokenValue(UUID.randomUUID().toString())
                                .header("alg", "none")
                                .subject(subject)
                                .claim("email", subject + "@example.com")
                                .build();
                        bookingService.create(jwt, new BookingCreateRequest(
                                equipmentId,
                                start,
                                start.plusSeconds(86_400),
                                DeliveryOption.PICKUP,
                                null));
                        return true;
                    } catch (ConflictException exception) {
                        return false;
                    }
                }));
            }
            ready.await();
            startTogether.countDown();

            int successfulReservations = 0;
            int conflicts = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulReservations++;
                } else {
                    conflicts++;
                }
            }
            assertThat(successfulReservations).isEqualTo(1);
            assertThat(conflicts).isEqualTo(9);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bookings WHERE equipment_id = ?",
                    Integer.class,
                    equipmentId)).isEqualTo(1);
            String bookingId = jdbcTemplate.queryForObject(
                    "SELECT id FROM bookings WHERE equipment_id = ?", String.class, equipmentId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT platform_fee_amount FROM bookings WHERE id = ?", java.math.BigDecimal.class, bookingId))
                    .isEqualByComparingTo("36.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT owner_commission_amount FROM bookings WHERE id = ?", java.math.BigDecimal.class, bookingId))
                    .isEqualByComparingTo("144.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT owner_earnings_estimated FROM bookings WHERE id = ?", java.math.BigDecimal.class, bookingId))
                    .isEqualByComparingTo("1056.00");
            jdbcTemplate.update("UPDATE equipment SET base_daily_rate = 9999.00, security_deposit = 1.00 WHERE id = ?",
                    equipmentId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT base_rental_amount FROM bookings WHERE id = ?", java.math.BigDecimal.class, bookingId))
                    .isEqualByComparingTo("1200.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT security_deposit_amount FROM bookings WHERE id = ?", java.math.BigDecimal.class, bookingId))
                    .isEqualByComparingTo("5000.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM booking_price_items WHERE booking_id = ?", Integer.class, bookingId))
                    .isEqualTo(6);
        } finally {
            executor.shutdownNow();
        }
    }
}
