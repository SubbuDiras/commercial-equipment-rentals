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
import com.marketplace.dev.DevToolsService;
import com.marketplace.exception.ConflictException;
import com.marketplace.owner.OwnerOnboardingRequest;
import com.marketplace.owner.OwnerOnboardingService;
import com.marketplace.storage.PrivateObjectStorage;
import com.marketplace.user.MyProfileResponse;
import com.marketplace.user.MyProfileService;
import com.marketplace.user.MyProfileUpdateRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

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
        // The dev profile reads these from environment variables only; supply test values here.
        registry.add("storage.s3.endpoint", () -> "http://localhost:9000");
        registry.add("storage.s3.region", () -> "ap-south-1");
        registry.add("storage.s3.access-key", () -> "test-access-key");
        registry.add("storage.s3.secret-key", () -> "test-secret-key");
        registry.add("storage.s3.bucket-name", () -> "test-private-bucket");
        registry.add("security.otp.secret", () -> "test-otp-secret");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
        registry.add("notifications.enabled", () -> "false");
        registry.add("payment.operations.enabled", () -> "false");
        registry.add("payment.operations.production-approved", () -> "false");
        registry.add("payment.operations.sandbox-only", () -> "true");
        registry.add("payment.razorpay.fake", () -> "false");
        registry.add("payment.razorpay.key-id", () -> "rzp_test_flyway");
        registry.add("payment.razorpay.key-secret", () -> "test-key-secret");
        registry.add("payment.razorpay.webhook-secret", () -> "test-webhook-secret");
        registry.add("payment.razorpay.api-base-url", () -> "http://localhost:18080/v1");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("keycloak.admin.base-url", () -> "http://localhost:8080");
        registry.add("keycloak.admin.realm", () -> "marketplace-realm");
        registry.add("keycloak.admin.client-id", () -> "marketplace-backend");
        registry.add("keycloak.admin.client-secret", () -> "");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private DevToolsService devToolsService;

    @Autowired
    private OwnerOnboardingService ownerOnboardingService;

    @Autowired
    private MyProfileService myProfileService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private PrivateObjectStorage objectStorage;

    @Test
    void appliesAllMigrationsToAnEmptyMysqlDatabase() {
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8", "9");
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
    void devSeedAndTimeShiftWorkAgainstTheRealSchema() {
        Jwt ownerJwt = Jwt.withTokenValue("seed-token").header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "seed-owner-" + UUID.randomUUID() + "@example.com")
                .build();

        DevToolsService.SeedResponse first = devToolsService.seedOwnerListing(ownerJwt);
        DevToolsService.SeedResponse second = devToolsService.seedOwnerListing(ownerJwt);

        assertThat(second.ownerProfileId()).isEqualTo(first.ownerProfileId());
        assertThat(second.equipmentId()).isNotEqualTo(first.equipmentId());
        assertThat(first.ownerRoleGranted()).as("Keycloak admin is not configured in this test").isFalse();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment e JOIN owner_profiles op ON op.id = e.owner_id
                WHERE op.id = ? AND e.status = 'ACTIVE' AND op.verification_status = 'VERIFIED'
                  AND op.razorpay_linked_account_id LIKE 'acc_FAKE%'
                """, Integer.class, first.ownerProfileId())).isEqualTo(2);
        assertThat(first.photoAttached()).isTrue();
        // Consecutive seeds are different kinds of equipment, each in its own category.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT e.category_id) FROM equipment e WHERE e.id IN (?, ?)
                """, Integer.class, first.equipmentId(), second.equipmentId())).isEqualTo(2);
        assertThat(first.title()).isNotEqualTo(second.title()).contains(" · DEV-");
        verify(objectStorage).put(startsWith("equipment/" + first.equipmentId() + "/images/"), eq("image/jpeg"),
                any(byte[].class));

        // A listing that lost its photo gets a sample one back; listings that already have one are left alone.
        jdbcTemplate.update("DELETE FROM equipment_images WHERE equipment_id = ?", first.equipmentId());
        DevToolsService.SamplePhotosResponse backfill = devToolsService.addSamplePhotos();
        assertThat(backfill.photosAttached()).isEqualTo(backfill.listingsWithoutPhotos()).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment_images
                WHERE equipment_id IN (?, ?) AND is_primary = TRUE AND uploaded_at IS NOT NULL
                """, Integer.class, first.equipmentId(), second.equipmentId())).isEqualTo(2);

        Instant start = Instant.now().plusSeconds(3 * 86_400L).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Jwt renterJwt = Jwt.withTokenValue("seed-renter").header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "seed-renter-" + UUID.randomUUID() + "@example.com")
                .build();
        String bookingId = bookingService.create(renterJwt, new BookingCreateRequest(
                first.equipmentId(), start, start.plusSeconds(2 * 86_400L), DeliveryOption.PICKUP, null)).bookingId();

        DevToolsService.ShiftTimeResponse shifted = devToolsService.shiftTime(bookingId, -96);

        assertThat(shifted.startTime()).isEqualTo(start.minusSeconds(96 * 3600L));
        assertThat(shifted.endTime()).isEqualTo(start.plusSeconds(2 * 86_400L - 96 * 3600L));
        assertThat(shifted.holdExpiresAt()).isBefore(Instant.now());
        assertThat(devToolsService.runScheduledJobs().expiredHolds()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId))
                .isEqualTo("REQUEST_EXPIRED");
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
                        'Hyderabad', '500001', 17.3850, 78.4867, 'ACTIVE')
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

    @Test
    void ownerApplicationWithAPhoneAlreadyInUseSaysSo() {
        String phone = "+9190" + String.valueOf(System.nanoTime()).substring(0, 8);
        ownerOnboardingService.submit(applicant(), ownerRequest(phone));

        assertThatThrownBy(() -> ownerOnboardingService.submit(applicant(), ownerRequest(phone)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phone number is already registered");
    }

    @Test
    void usersReadAndUpdateTheirOwnProfile() {
        Jwt me = Jwt.withTokenValue("me").header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "me-" + UUID.randomUUID() + "@example.com")
                .claim("given_name", "Meera").claim("family_name", "Rao")
                .claim("realm_access", java.util.Map.of("roles", List.of("ROLE_RENTER", "default-roles-marketplace-realm")))
                .build();

        MyProfileResponse created = myProfileService.get(me);
        assertThat(created.firstName()).isEqualTo("Meera");
        assertThat(created.roles()).containsExactly("ROLE_RENTER");
        assertThat(created.signInNameUpdated()).isNull();

        String phone = "+9191" + String.valueOf(System.nanoTime()).substring(0, 8);
        MyProfileResponse updated = myProfileService.update(me, new MyProfileUpdateRequest(" Meera ", "Rao-Iyer", phone));
        assertThat(updated.firstName()).isEqualTo("Meera");
        assertThat(updated.lastName()).isEqualTo("Rao-Iyer");
        assertThat(updated.phone()).isEqualTo(phone);
        assertThat(updated.signInNameUpdated()).as("Keycloak admin is not configured in this test").isFalse();

        assertThat(myProfileService.update(me, new MyProfileUpdateRequest("Meera", "Rao", "")).phone()).isNull();

        Jwt other = applicant();
        myProfileService.update(other, new MyProfileUpdateRequest("Other", "Person", phone));
        assertThatThrownBy(() -> myProfileService.update(me, new MyProfileUpdateRequest("Meera", "Rao", phone)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phone number is already registered");
    }

    private static Jwt applicant() {
        return Jwt.withTokenValue("applicant").header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "applicant-" + UUID.randomUUID() + "@example.com")
                .build();
    }

    private static OwnerOnboardingRequest ownerRequest(String phone) {
        return new OwnerOnboardingRequest("INDIVIDUAL", "", "", "", "987654321098", "HDFC0001234",
                "Asha Applicant", "Asha", "Applicant", phone);
    }
}
