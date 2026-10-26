package com.rental.Marketplace;

import com.marketplace.equipment.EquipmentCatalogueService;
import com.marketplace.equipment.EquipmentCategoryAdminService;
import com.marketplace.equipment.EquipmentCategoryRequest;
import com.marketplace.equipment.EquipmentListingRequest;
import com.marketplace.equipment.EquipmentListingResponse;
import com.marketplace.equipment.EquipmentImageUploadRequest;
import com.marketplace.equipment.EquipmentMediaService;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.storage.PrivateObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@ActiveProfiles("test")
class EquipmentCatalogueTests {

    private static final String CATEGORY_ID = "category-1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EquipmentCatalogueService catalogueService;

    @Autowired
    private EquipmentMediaService mediaService;

    @Autowired
    private EquipmentCategoryAdminService categoryAdminService;

    @MockBean
    private PrivateObjectStorage objectStorage;

    @BeforeEach
    void createCatalogueSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS bookings");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment_images");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment_categories");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id CHAR(36) PRIMARY KEY,
                    keycloak_subject_id VARCHAR(64) NOT NULL UNIQUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_profiles (
                    id CHAR(36) PRIMARY KEY,
                    user_id CHAR(36) NOT NULL,
                    verification_status VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment_categories (
                    id CHAR(36) PRIMARY KEY,
                    parent_category_id CHAR(36),
                    slug VARCHAR(100) NOT NULL,
                    name VARCHAR(150) NOT NULL,
                    description CLOB,
                    is_active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment (
                    id CHAR(36) PRIMARY KEY,
                    owner_id CHAR(36) NOT NULL,
                    category_id CHAR(36) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    description CLOB NOT NULL,
                    manufacturer VARCHAR(100) NOT NULL,
                    model_number VARCHAR(100) NOT NULL,
                    serial_number VARCHAR(100),
                    year_of_manufacture INT,
                    condition_grade VARCHAR(30) NOT NULL,
                    specifications_json CLOB,
                    base_daily_rate DECIMAL(12, 2) NOT NULL,
                    base_weekly_rate DECIMAL(12, 2),
                    security_deposit DECIMAL(12, 2) NOT NULL,
                    minimum_rental_days INT NOT NULL,
                    delivery_supported BOOLEAN NOT NULL,
                    pickup_supported BOOLEAN NOT NULL,
                    delivery_charge_flat DECIMAL(12, 2),
                    address_line VARCHAR(255) NOT NULL,
                    city VARCHAR(100) NOT NULL,
                    postal_code VARCHAR(20) NOT NULL,
                    latitude DECIMAL(10, 7) NOT NULL,
                    longitude DECIMAL(10, 7) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    version INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE bookings (
                    id CHAR(36) PRIMARY KEY,
                    equipment_id CHAR(36) NOT NULL,
                    start_time TIMESTAMP(6) NOT NULL,
                    end_time TIMESTAMP(6) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    hold_expires_at TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment_images (
                    id CHAR(36) PRIMARY KEY,
                    equipment_id CHAR(36) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL,
                    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                    display_order INT NOT NULL DEFAULT 0,
                    content_type VARCHAR(100),
                    uploaded_at TIMESTAMP(6),
                    upload_expires_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id CHAR(36) PRIMARY KEY,
                    actor_user_id CHAR(36),
                    action VARCHAR(100) NOT NULL,
                    entity_name VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(64) NOT NULL,
                    details_json CLOB
                )
                """);
        jdbcTemplate.update("INSERT INTO users (id, keycloak_subject_id) VALUES ('user-1', 'owner-1'), ('user-2', 'owner-2')");
        jdbcTemplate.update("""
                INSERT INTO owner_profiles (id, user_id, verification_status)
                VALUES ('owner-profile-1', 'user-1', 'VERIFIED'), ('owner-profile-2', 'user-2', 'VERIFIED')
                """);
        jdbcTemplate.update("""
                INSERT INTO equipment_categories (id, slug, name, description, is_active)
                VALUES (?, 'demolition-hammers', 'Demolition Hammers', 'Test category', TRUE)
                """, CATEGORY_ID);
    }

    @Test
    void listingIsHiddenUntilAdminApprovalThenAppearsInPublicSearch() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(catalogueService.search(null, null, null, null, 0, 20).totalElements()).isZero();

        EquipmentListingResponse pending = catalogueService.submit(ownerJwt("owner-1"), draft.id());
        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(catalogueService.pendingListings()).hasSize(1);
        assertThat(catalogueService.search(null, null, null, null, 0, 20).totalElements()).isZero();

        EquipmentListingResponse approved = catalogueService.review(draft.id(), adminJwt(), true, null);

        assertThat(approved.status()).isEqualTo("ACTIVE");
        assertThat(catalogueService.search(null, "bengaluru", null, new BigDecimal("1500"), 0, 20)
                .content()).extracting(EquipmentListingResponse::id).containsExactly(draft.id());
        EquipmentListingResponse publicListing = catalogueService.getPublicListing(draft.id());
        assertThat(publicListing.title()).isEqualTo("Demolition hammer");
        assertThat(publicListing.serialNumber()).isNull();
        assertThat(publicListing.addressLine()).isNull();
        assertThat(publicListing.latitude()).isNull();
    }

    @Test
    void ownerCannotModifyAnotherOwnersListing() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));

        assertThatThrownBy(() -> catalogueService.update(ownerJwt("owner-2"), draft.id(), request(CATEGORY_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveCategoriesCannotBeUsedForListings() {
        jdbcTemplate.update("UPDATE equipment_categories SET is_active = FALSE WHERE id = ?", CATEGORY_ID);

        assertThatThrownBy(() -> catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active equipment category");
    }

    @Test
    void dateFilteredSearchExcludesOverlappingBookingsAndAllowsAdjacentIntervals() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));
        catalogueService.submit(ownerJwt("owner-1"), draft.id());
        catalogueService.review(draft.id(), adminJwt(), true, null);
        Instant reservedStart = Instant.parse("2026-11-01T09:00:00Z");
        Instant reservedEnd = Instant.parse("2026-11-02T09:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO bookings (id, equipment_id, start_time, end_time, status)
                VALUES (?, ?, ?, ?, 'CONFIRMED')
                """,
                "booking-1", draft.id(), Timestamp.from(reservedStart), Timestamp.from(reservedEnd));

        assertThat(catalogueService.search(null, null, null, null,
                Instant.parse("2026-11-02T09:00:00Z"), Instant.parse("2026-11-03T09:00:00Z"), 0, 20)
                .content()).hasSize(1);
        assertThat(catalogueService.search(null, null, null, null,
                Instant.parse("2026-11-01T12:00:00Z"), Instant.parse("2026-11-02T12:00:00Z"), 0, 20)
                .content()).isEmpty();
    }

    @Test
    void dateFilteredSearchIgnoresExpiredRequestHoldsAndRejectsIncompleteRanges() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));
        catalogueService.submit(ownerJwt("owner-1"), draft.id());
        catalogueService.review(draft.id(), adminJwt(), true, null);
        jdbcTemplate.update("""
                INSERT INTO bookings (id, equipment_id, start_time, end_time, status, hold_expires_at)
                VALUES (?, ?, ?, ?, 'REQUESTED', ?)
                """,
                "booking-expired", draft.id(),
                Timestamp.from(Instant.parse("2026-11-01T09:00:00Z")),
                Timestamp.from(Instant.parse("2026-11-02T09:00:00Z")),
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(catalogueService.search(null, null, null, null,
                Instant.parse("2026-11-01T12:00:00Z"), Instant.parse("2026-11-02T12:00:00Z"), 0, 20)
                .content()).hasSize(1);
        assertThatThrownBy(() -> catalogueService.search(null, null, null, null,
                Instant.parse("2026-11-01T12:00:00Z"), null, 0, 20))
                .isInstanceOf(com.marketplace.exception.BadRequestException.class)
                .hasMessageContaining("provided together");
    }

    @Test
    void ownerCanUploadListingPhotosAndOtherOwnersCannotReadThem() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(objectStorage.presignPut(anyString(), anyString(), anyLong()))
                .thenReturn(new PrivateObjectStorage.PresignedUpload("https://storage.test/upload", expiresAt));
        when(objectStorage.existsWithinLimit(anyString())).thenReturn(true);
        when(objectStorage.presignGet(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn("https://storage.test/image");

        var upload = mediaService.presign(draft.id(),
                new EquipmentImageUploadRequest("image/jpeg", 1024), ownerJwt("owner-1"));
        var completed = mediaService.complete(draft.id(), upload.imageId(), ownerJwt("owner-1"));

        assertThat(completed.primary()).isTrue();
        assertThat(completed.imageUrl()).isEqualTo("https://storage.test/image");
        assertThat(mediaService.ownerImages(draft.id(), ownerJwt("owner-1"))).hasSize(1);
        assertThatThrownBy(() -> mediaService.ownerImages(draft.id(), ownerJwt("owner-2")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifiedOwnerStatusIsRequiredToSubmitListing() {
        EquipmentListingResponse draft = catalogueService.create(ownerJwt("owner-1"), request(CATEGORY_ID));
        jdbcTemplate.update("UPDATE owner_profiles SET verification_status = 'UNDER_REVIEW' WHERE id = 'owner-profile-1'");

        assertThatThrownBy(() -> catalogueService.submit(ownerJwt("owner-1"), draft.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void administratorCanManageCategoryHierarchyWithoutCreatingCycles() {
        var parent = categoryAdminService.create(
                new EquipmentCategoryRequest(null, "power-tools", "Power Tools", null, true), adminJwt());
        var child = categoryAdminService.create(
                new EquipmentCategoryRequest(parent.id(), "rotary-hammers", "Rotary Hammers", null, true), adminJwt());

        assertThat(child.parentCategoryId()).isEqualTo(parent.id());
        assertThatThrownBy(() -> categoryAdminService.update(parent.id(),
                new EquipmentCategoryRequest(child.id(), "power-tools", "Power Tools", null, true), adminJwt()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ancestor");
        assertThatThrownBy(() -> categoryAdminService.update(parent.id(),
                new EquipmentCategoryRequest(null, "power-tools", "Power Tools", null, false), adminJwt()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("child categories");
    }

    private static EquipmentListingRequest request(String categoryId) {
        return new EquipmentListingRequest(
                categoryId,
                "Demolition hammer",
                "Test listing description",
                "Example",
                "Model-1",
                null,
                2024,
                "GOOD",
                Map.of("power", "1500W"),
                new BigDecimal("1200.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("5000.00"),
                1,
                true,
                true,
                new BigDecimal("350.00"),
                "Shop 14, Whitefield",
                "Bengaluru",
                "560066",
                new BigDecimal("12.9698000"),
                new BigDecimal("77.7499000"));
    }

    private static Jwt ownerJwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-subject")
                .build();
    }
}
