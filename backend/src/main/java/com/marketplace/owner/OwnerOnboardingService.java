package com.marketplace.owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OwnerOnboardingService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OwnerOnboardingService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OwnerProfileResponse submit(Jwt jwt, OwnerOnboardingRequest request) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ConflictException("The authenticated account must provide a subject and email address.");
        }

        try {
            String userId = findUserId(subject);
            if (userId == null) {
                userId = UUID.randomUUID().toString();
                jdbcTemplate.update("""
                        INSERT INTO users
                            (id, keycloak_subject_id, email, phone, first_name, last_name)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        userId, subject, email, request.phone(), request.firstName(), request.lastName());
            } else if (ownerProfileExists(userId)) {
                throw new ConflictException("An owner profile already exists for this account.");
            }

            String organizationId = UUID.randomUUID().toString();
            String businessName = request.companyName() == null || request.companyName().isBlank()
                    ? request.firstName() + " " + request.lastName()
                    : request.companyName().trim();
            jdbcTemplate.update("""
                    INSERT INTO organizations
                        (id, legal_name, trade_name, gstin, pan, created_by_user_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    organizationId, businessName, request.companyName(), emptyToNull(request.gstin()),
                    emptyToNull(request.pan()), userId);

            String profileId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO owner_profiles
                        (id, user_id, organization_id, business_type, bank_account_number, bank_ifsc,
                         bank_account_holder_name, verification_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    profileId, userId, organizationId, request.businessType(), request.bankAccountNumber(),
                    request.bankIfsc(), request.bankAccountHolderName(), OwnerVerificationStatus.UNDER_REVIEW.name());

            writeAudit(userId, "OWNER_ONBOARDING_SUBMITTED", "owner_profiles", profileId,
                    Map.of("businessType", request.businessType(), "status", OwnerVerificationStatus.UNDER_REVIEW.name()));

            return new OwnerProfileResponse(profileId, OwnerVerificationStatus.UNDER_REVIEW.name(), Instant.now());
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("The submitted owner details conflict with an existing account or organization.");
        }
    }

    @Transactional(readOnly = true)
    public List<OwnerReviewResponse> listForReview(OwnerVerificationStatus status) {
        return jdbcTemplate.query("""
                SELECT op.id, u.first_name, u.last_name, u.email, o.legal_name, op.business_type,
                       op.verification_status, op.created_at
                FROM owner_profiles op
                JOIN users u ON u.id = op.user_id
                LEFT JOIN organizations o ON o.id = op.organization_id
                WHERE op.verification_status = ?
                ORDER BY op.created_at ASC
                """,
                (resultSet, rowNumber) -> mapReview(resultSet),
                status.name());
    }

    @Transactional
    public OwnerReviewResponse approve(String ownerProfileId, Jwt adminJwt) {
        return review(ownerProfileId, adminJwt, OwnerVerificationStatus.VERIFIED, null);
    }

    @Transactional
    public OwnerReviewResponse reject(String ownerProfileId, Jwt adminJwt, String reason) {
        return review(ownerProfileId, adminJwt, OwnerVerificationStatus.REJECTED, reason);
    }

    private OwnerReviewResponse review(
            String ownerProfileId,
            Jwt adminJwt,
            OwnerVerificationStatus targetStatus,
            String reason
    ) {
        OwnerReviewResponse owner = findReview(ownerProfileId);
        if (!OwnerVerificationStatus.UNDER_REVIEW.name().equals(owner.status())) {
            throw new ConflictException("Only owner profiles under review can be approved or rejected.");
        }

        String adminUserId = findUserId(adminJwt.getSubject());
        int updatedRows;
        if (targetStatus == OwnerVerificationStatus.VERIFIED) {
            updatedRows = jdbcTemplate.update("""
                    UPDATE owner_profiles
                    SET verification_status = ?, verified_at = CURRENT_TIMESTAMP(6),
                        verified_by_user_id = ?, rejection_reason = NULL
                    WHERE id = ? AND verification_status = ?
                    """,
                    targetStatus.name(), adminUserId, ownerProfileId, OwnerVerificationStatus.UNDER_REVIEW.name());
        } else {
            updatedRows = jdbcTemplate.update("""
                    UPDATE owner_profiles
                    SET verification_status = ?, verified_at = NULL,
                        verified_by_user_id = ?, rejection_reason = ?
                    WHERE id = ? AND verification_status = ?
                    """,
                    targetStatus.name(), adminUserId, reason, ownerProfileId, OwnerVerificationStatus.UNDER_REVIEW.name());
        }
        if (updatedRows != 1) {
            throw new ConflictException("The owner profile was reviewed by another administrator.");
        }

        Map<String, String> details = reason == null
                ? Map.of("status", targetStatus.name(), "adminSubject", adminJwt.getSubject())
                : Map.of("status", targetStatus.name(), "adminSubject", adminJwt.getSubject(), "reason", reason);
        writeAudit(adminUserId, "OWNER_VERIFICATION_" + targetStatus.name(), "owner_profiles", ownerProfileId, details);
        return new OwnerReviewResponse(owner.ownerProfileId(), owner.firstName(), owner.lastName(), owner.email(),
                owner.businessName(), owner.businessType(), targetStatus.name(), owner.submittedAt());
    }

    private OwnerReviewResponse findReview(String ownerProfileId) {
        OwnerReviewResponse owner = jdbcTemplate.query("""
                        SELECT op.id, u.first_name, u.last_name, u.email, o.legal_name, op.business_type,
                               op.verification_status, op.created_at
                        FROM owner_profiles op
                        JOIN users u ON u.id = op.user_id
                        LEFT JOIN organizations o ON o.id = op.organization_id
                        WHERE op.id = ?
                        """,
                resultSet -> resultSet.next() ? mapReview(resultSet) : null,
                ownerProfileId);
        if (owner == null) {
            throw new ResourceNotFoundException("Owner profile not found.");
        }
        return owner;
    }

    private String findUserId(String subject) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE keycloak_subject_id = ?",
                    String.class,
                    subject);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private boolean ownerProfileExists(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_profiles WHERE user_id = ?",
                Integer.class,
                userId);
        return count != null && count > 0;
    }

    private void writeAudit(String actorUserId, String action, String entityName, String entityId,
                            Map<String, String> details) {
        String json;
        try {
            json = objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize owner verification audit details.", exception);
        }
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), actorUserId, action, entityName, entityId, json);
    }

    private static OwnerReviewResponse mapReview(ResultSet resultSet) throws SQLException {
        return new OwnerReviewResponse(
                resultSet.getString("id"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("email"),
                resultSet.getString("legal_name"),
                resultSet.getString("business_type"),
                resultSet.getString("verification_status"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
