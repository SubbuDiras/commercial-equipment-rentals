package com.rental.Marketplace;

import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.owner.OwnerDocumentService;
import com.marketplace.owner.OwnerDocumentUploadRequest;
import com.marketplace.owner.OwnerOnboardingRequest;
import com.marketplace.owner.OwnerOnboardingService;
import com.marketplace.owner.OwnerProfileResponse;
import com.marketplace.owner.OwnerReviewResponse;
import com.marketplace.owner.OwnerVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import com.marketplace.storage.PrivateObjectStorage;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@ActiveProfiles("test")
class OwnerOnboardingServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OwnerOnboardingService ownerOnboardingService;

    @Autowired
    private OwnerDocumentService ownerDocumentService;

    @MockBean
    private PrivateObjectStorage objectStorage;

    @BeforeEach
    void createOwnerSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_verification_documents");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS organizations");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id CHAR(36) PRIMARY KEY,
                    keycloak_subject_id VARCHAR(64) NOT NULL UNIQUE,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    phone VARCHAR(20) NOT NULL UNIQUE,
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE organizations (
                    id CHAR(36) PRIMARY KEY,
                    legal_name VARCHAR(255) NOT NULL,
                    trade_name VARCHAR(255),
                    gstin VARCHAR(15) UNIQUE,
                    pan VARCHAR(10),
                    created_by_user_id CHAR(36) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_profiles (
                    id CHAR(36) PRIMARY KEY,
                    user_id CHAR(36) NOT NULL UNIQUE,
                    organization_id CHAR(36),
                    business_type VARCHAR(30) NOT NULL,
                    bank_account_number VARCHAR(34),
                    bank_ifsc VARCHAR(11),
                    bank_account_holder_name VARCHAR(255),
                    verification_status VARCHAR(30) NOT NULL,
                    rejection_reason VARCHAR(2000),
                    verified_at TIMESTAMP(6),
                    verified_by_user_id CHAR(36),
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id CHAR(36) PRIMARY KEY,
                    actor_user_id CHAR(36),
                    action VARCHAR(100) NOT NULL,
                    entity_name VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(64) NOT NULL,
                    details_json CLOB,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_verification_documents (
                    id CHAR(36) PRIMARY KEY,
                    owner_profile_id CHAR(36) NOT NULL,
                    document_type VARCHAR(50) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    file_mime_type VARCHAR(100) NOT NULL,
                    file_size_bytes BIGINT NOT NULL,
                    uploaded_at TIMESTAMP(6),
                    upload_expires_at TIMESTAMP(6),
                    verified_status VARCHAR(30) NOT NULL
                )
                """);
    }

    @Test
    void submitsOwnerForReviewAndAuditsApproval() {
        OwnerProfileResponse submitted = ownerOnboardingService.submit(ownerJwt(), request());

        assertThat(submitted.status()).isEqualTo(OwnerVerificationStatus.UNDER_REVIEW.name());
        assertThat(ownerOnboardingService.listForReview(OwnerVerificationStatus.UNDER_REVIEW))
                .extracting(OwnerReviewResponse::ownerProfileId)
                .containsExactly(submitted.ownerProfileId());

        OwnerReviewResponse approved = ownerOnboardingService.approve(submitted.ownerProfileId(), adminJwt());

        assertThat(approved.status()).isEqualTo(OwnerVerificationStatus.VERIFIED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verification_status FROM owner_profiles WHERE id = ?",
                String.class,
                submitted.ownerProfileId())).isEqualTo(OwnerVerificationStatus.VERIFIED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND action = ?",
                Integer.class,
                submitted.ownerProfileId(),
                "OWNER_VERIFICATION_VERIFIED")).isEqualTo(1);
    }

    @Test
    void recordsRejectionReasonAndPreventsSecondDecision() {
        OwnerProfileResponse submitted = ownerOnboardingService.submit(ownerJwt(), request());

        OwnerReviewResponse rejected = ownerOnboardingService.reject(
                submitted.ownerProfileId(), adminJwt(), "Please submit a clearer bank document.");

        assertThat(rejected.status()).isEqualTo(OwnerVerificationStatus.REJECTED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM owner_profiles WHERE id = ?",
                String.class,
                submitted.ownerProfileId())).isEqualTo("Please submit a clearer bank document.");
        assertThatThrownBy(() -> ownerOnboardingService.approve(submitted.ownerProfileId(), adminJwt()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void preventsSubmittingASecondOwnerProfileForTheSameAccount() {
        ownerOnboardingService.submit(ownerJwt(), request());

        assertThatThrownBy(() -> ownerOnboardingService.submit(ownerJwt(), request()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void storesKycDocumentsAsPrivateUploadsAndRestrictsAccessToTheOwner() {
        OwnerProfileResponse profile = ownerOnboardingService.submit(ownerJwt(), request());
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(objectStorage.presignPut(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new PrivateObjectStorage.PresignedUpload("https://storage.test/upload", expiresAt));
        when(objectStorage.existsWithinLimit(anyString())).thenReturn(true);

        var upload = ownerDocumentService.presign(profile.ownerProfileId(),
                new OwnerDocumentUploadRequest("IDENTITY", "identity.pdf", "application/pdf", 512),
                ownerJwt());

        assertThat(upload.uploadUrl()).isEqualTo("https://storage.test/upload");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT verified_status FROM owner_verification_documents WHERE id = ?",
                String.class, upload.documentId())).isEqualTo("UPLOAD_PENDING");

        var completed = ownerDocumentService.complete(profile.ownerProfileId(), upload.documentId(), ownerJwt());

        assertThat(completed.status()).isEqualTo("PENDING");
        assertThat(completed.uploadedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_id = ? AND action = ?",
                Integer.class, profile.ownerProfileId(), "OWNER_KYC_DOCUMENT_UPLOADED")).isEqualTo(1);
        assertThatThrownBy(() -> ownerDocumentService.ownerDocuments(profile.ownerProfileId(), adminJwt()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static OwnerOnboardingRequest request() {
        return new OwnerOnboardingRequest(
                "PROPRIETORSHIP",
                "Bangalore Tool Rentals",
                "29ABCDE1234F1Z5",
                "ABCDE1234F",
                "987654321098",
                "HDFC0001234",
                "Bangalore Tool Rentals",
                "Suresh",
                "Patel",
                "+919876543210");
    }

    private static Jwt ownerJwt() {
        return Jwt.withTokenValue("owner-token")
                .header("alg", "none")
                .subject("owner-subject")
                .claim("email", "owner@example.com")
                .build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-subject")
                .build();
    }
}
