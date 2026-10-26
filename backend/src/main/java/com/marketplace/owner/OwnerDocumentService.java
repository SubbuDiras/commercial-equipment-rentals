package com.marketplace.owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.storage.PrivateObjectStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OwnerDocumentService {

    private final JdbcTemplate jdbcTemplate;
    private final PrivateObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    public OwnerDocumentService(
            JdbcTemplate jdbcTemplate,
            PrivateObjectStorage objectStorage,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OwnerDocumentUploadResponse presign(
            String ownerProfileId,
            OwnerDocumentUploadRequest request,
            Jwt jwt
    ) {
        String userId = requireOwnerProfile(ownerProfileId, jwt.getSubject());
        String status = jdbcTemplate.queryForObject(
                "SELECT verification_status FROM owner_profiles WHERE id = ?",
                String.class, ownerProfileId);
        if (OwnerVerificationStatus.VERIFIED.name().equals(status)
                || OwnerVerificationStatus.SUSPENDED.name().equals(status)) {
            throw new ConflictException("Documents cannot be changed for this owner profile.");
        }

        String documentId = UUID.randomUUID().toString();
        String extension = extensionFor(request.contentType());
        String objectKey = "owners/" + ownerProfileId + "/kyc/" + documentId + "." + extension;
        PrivateObjectStorage.PresignedUpload upload =
                objectStorage.presignPut(objectKey, request.contentType(), request.contentLength());
        jdbcTemplate.update("""
                INSERT INTO owner_verification_documents
                    (id, owner_profile_id, document_type, storage_object_key, original_filename,
                     file_mime_type, file_size_bytes, uploaded_at, upload_expires_at, verified_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 'UPLOAD_PENDING')
                """,
                documentId, ownerProfileId, request.documentType(), objectKey,
                safeFilename(request.originalFilename()), request.contentType(), request.contentLength(),
                Timestamp.from(upload.expiresAt()));
        writeAudit(userId, "OWNER_KYC_UPLOAD_PRESIGNED", ownerProfileId,
                Map.of("documentId", documentId, "documentType", request.documentType()));
        return new OwnerDocumentUploadResponse(
                documentId, upload.uploadUrl(), request.contentType(), upload.expiresAt());
    }

    @Transactional
    public OwnerDocumentResponse complete(
            String ownerProfileId,
            String documentId,
            Jwt jwt
    ) {
        String userId = requireOwnerProfile(ownerProfileId, jwt.getSubject());
        DocumentRow document = findDocument(ownerProfileId, documentId);
        if (document == null || !"UPLOAD_PENDING".equals(document.status())
                || document.uploadExpiresAt() == null
                || !document.uploadExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("The document upload is expired or has already been completed.");
        }
        if (!objectStorage.existsWithinLimit(document.objectKey())) {
            throw new ConflictException("The uploaded document is missing or exceeds the permitted size.");
        }
        jdbcTemplate.update("""
                UPDATE owner_verification_documents
                SET verified_status = 'PENDING', uploaded_at = CURRENT_TIMESTAMP(6), upload_expires_at = NULL
                WHERE id = ? AND owner_profile_id = ? AND verified_status = 'UPLOAD_PENDING'
                """, documentId, ownerProfileId);
        writeAudit(userId, "OWNER_KYC_DOCUMENT_UPLOADED", ownerProfileId,
                Map.of("documentId", documentId, "documentType", document.documentType()));
        return documentResponse(documentId);
    }

    @Transactional(readOnly = true)
    public List<OwnerDocumentResponse> ownerDocuments(String ownerProfileId, Jwt jwt) {
        requireOwnerProfile(ownerProfileId, jwt.getSubject());
        return listDocuments(ownerProfileId);
    }

    @Transactional(readOnly = true)
    public List<OwnerDocumentResponse> adminDocuments(String ownerProfileId) {
        requireProfileExists(ownerProfileId);
        return listDocuments(ownerProfileId);
    }

    @Transactional
    public String ownerDownloadUrl(String ownerProfileId, String documentId, Jwt jwt) {
        requireOwnerProfile(ownerProfileId, jwt.getSubject());
        return downloadUrl(ownerProfileId, documentId, jwt.getSubject(), "OWNER_KYC_DOCUMENT_ACCESSED");
    }

    @Transactional
    public String adminDownloadUrl(String ownerProfileId, String documentId, Jwt adminJwt) {
        requireProfileExists(ownerProfileId);
        return downloadUrl(ownerProfileId, documentId, adminJwt.getSubject(), "OWNER_KYC_DOCUMENT_ACCESSED_BY_ADMIN");
    }

    private String downloadUrl(String ownerProfileId, String documentId, String actorSubject, String action) {
        DocumentRow document = findDocument(ownerProfileId, documentId);
        if (document == null || (!"PENDING".equals(document.status()) && !"VERIFIED".equals(document.status()))) {
            throw new ResourceNotFoundException("Uploaded owner document not found.");
        }
        String actorUserId = findUserId(actorSubject);
        writeAudit(actorUserId, action, ownerProfileId, Map.of("documentId", documentId));
        return objectStorage.presignGet(document.objectKey());
    }

    private List<OwnerDocumentResponse> listDocuments(String ownerProfileId) {
        return jdbcTemplate.query("""
                        SELECT id, document_type, original_filename, file_mime_type, file_size_bytes,
                               verified_status, uploaded_at, upload_expires_at
                        FROM owner_verification_documents
                        WHERE owner_profile_id = ?
                        ORDER BY uploaded_at DESC, id
                        """,
                (rs, row) -> new OwnerDocumentResponse(
                        rs.getString("id"),
                        rs.getString("document_type"),
                        rs.getString("original_filename"),
                        rs.getString("file_mime_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getString("verified_status"),
                        rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toInstant(),
                        rs.getTimestamp("upload_expires_at") == null
                                ? null : rs.getTimestamp("upload_expires_at").toInstant()),
                ownerProfileId);
    }

    private OwnerDocumentResponse documentResponse(String documentId) {
        return jdbcTemplate.query("""
                        SELECT id, document_type, original_filename, file_mime_type, file_size_bytes,
                               verified_status, uploaded_at, upload_expires_at
                        FROM owner_verification_documents WHERE id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new ResourceNotFoundException("Owner document not found.");
                    }
                    return new OwnerDocumentResponse(
                            rs.getString("id"), rs.getString("document_type"), rs.getString("original_filename"),
                            rs.getString("file_mime_type"), rs.getLong("file_size_bytes"),
                            rs.getString("verified_status"),
                            rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toInstant(),
                            rs.getTimestamp("upload_expires_at") == null
                                    ? null : rs.getTimestamp("upload_expires_at").toInstant());
                },
                documentId);
    }

    private DocumentRow findDocument(String ownerProfileId, String documentId) {
        return jdbcTemplate.query("""
                        SELECT storage_object_key, document_type, verified_status, upload_expires_at
                        FROM owner_verification_documents WHERE id = ? AND owner_profile_id = ?
                        """,
                rs -> rs.next()
                        ? new DocumentRow(rs.getString("storage_object_key"), rs.getString("document_type"),
                        rs.getString("verified_status"), rs.getTimestamp("upload_expires_at") == null
                        ? null : rs.getTimestamp("upload_expires_at").toInstant())
                        : null,
                documentId, ownerProfileId);
    }

    private String requireOwnerProfile(String ownerProfileId, String subject) {
        String userId = jdbcTemplate.query("""
                        SELECT u.id
                        FROM users u
                        JOIN owner_profiles op ON op.user_id = u.id
                        WHERE u.keycloak_subject_id = ? AND op.id = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                subject, ownerProfileId);
        if (userId == null) {
            throw new ResourceNotFoundException("Owner profile not found.");
        }
        return userId;
    }

    private void requireProfileExists(String ownerProfileId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_profiles WHERE id = ?", Integer.class, ownerProfileId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Owner profile not found.");
        }
    }

    private String findUserId(String subject) {
        return jdbcTemplate.query("""
                        SELECT id FROM users WHERE keycloak_subject_id = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null, subject);
    }

    private void writeAudit(String actorUserId, String action, String ownerProfileId, Map<String, String> details) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                    VALUES (?, ?, ?, 'owner_profiles', ?, ?)
                    """,
                    UUID.randomUUID().toString(), actorUserId, action, ownerProfileId,
                    objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize KYC document audit details.", exception);
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new ConflictException("KYC documents must be PDF, JPEG, or PNG.");
        };
    }

    private static String safeFilename(String filename) {
        String sanitized = filename.replace('\\', '_').replace('/', '_').trim();
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new ConflictException("A valid document filename is required.");
        }
        return sanitized;
    }

    private record DocumentRow(String objectKey, String documentType, String status, Instant uploadExpiresAt) {
    }
}
