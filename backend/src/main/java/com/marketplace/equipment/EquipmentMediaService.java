package com.marketplace.equipment;

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
import java.util.UUID;

@Service
public class EquipmentMediaService {

    private final JdbcTemplate jdbcTemplate;
    private final PrivateObjectStorage objectStorage;

    public EquipmentMediaService(JdbcTemplate jdbcTemplate, PrivateObjectStorage objectStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public EquipmentImageUploadResponse presign(
            String equipmentId,
            EquipmentImageUploadRequest request,
            Jwt jwt
    ) {
        lockEditableListing(equipmentId, jwt.getSubject());
        String extension = switch (request.contentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ConflictException("Equipment images must use JPEG, PNG, or WebP.");
        };
        String imageId = UUID.randomUUID().toString();
        String key = "equipment/" + equipmentId + "/images/" + imageId + "." + extension;
        PrivateObjectStorage.PresignedUpload upload =
                objectStorage.presignPut(key, request.contentType(), request.contentLength());
        jdbcTemplate.update("""
                INSERT INTO equipment_images
                    (id, equipment_id, storage_object_key, is_primary, display_order, content_type,
                     uploaded_at, upload_expires_at)
                VALUES (?, ?, ?, FALSE, 0, ?, NULL, ?)
                """,
                imageId, equipmentId, key, request.contentType(), Timestamp.from(upload.expiresAt()));
        return new EquipmentImageUploadResponse(imageId, upload.uploadUrl(), request.contentType(), upload.expiresAt());
    }

    @Transactional
    public EquipmentImageResponse complete(String equipmentId, String imageId, Jwt jwt) {
        lockEditableListing(equipmentId, jwt.getSubject());
        ImageRow image = findImage(equipmentId, imageId);
        if (image == null || image.uploadExpiresAt() == null || !image.uploadExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("The image upload is expired or has already been completed.");
        }
        if (!objectStorage.existsWithinLimit(image.objectKey())) {
            throw new ConflictException("The uploaded image is missing or exceeds the permitted size.");
        }
        Integer existingImages = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment_images
                WHERE equipment_id = ? AND uploaded_at IS NOT NULL
                """, Integer.class, equipmentId);
        int displayOrder = existingImages == null ? 0 : existingImages;
        boolean primary = displayOrder == 0;
        int updated = jdbcTemplate.update("""
                UPDATE equipment_images
                SET uploaded_at = CURRENT_TIMESTAMP(6), upload_expires_at = NULL,
                    is_primary = ?, display_order = ?
                WHERE id = ? AND equipment_id = ? AND uploaded_at IS NULL
                """,
                primary, displayOrder, imageId, equipmentId);
        if (updated != 1) {
            throw new ConflictException("The image upload has already been completed.");
        }
        return new EquipmentImageResponse(imageId, objectStorage.presignGet(image.objectKey(), false),
                primary, displayOrder);
    }

    @Transactional(readOnly = true)
    public List<EquipmentImageResponse> ownerImages(String equipmentId, Jwt jwt) {
        requireOwnedListing(equipmentId, jwt.getSubject());
        return images(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentImageResponse> publicImages(String equipmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment e
                JOIN owner_profiles op ON op.id = e.owner_id
                JOIN equipment_categories c ON c.id = e.category_id
                WHERE e.id = ? AND e.status = 'ACTIVE'
                  AND op.verification_status = 'VERIFIED' AND c.is_active = TRUE
                """, Integer.class, equipmentId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Active equipment listing not found.");
        }
        return images(equipmentId);
    }

    private List<EquipmentImageResponse> images(String equipmentId) {
        return jdbcTemplate.query("""
                        SELECT id, storage_object_key, is_primary, display_order
                        FROM equipment_images
                        WHERE equipment_id = ? AND uploaded_at IS NOT NULL
                        ORDER BY is_primary DESC, display_order, id
                        """,
                (rs, row) -> new EquipmentImageResponse(
                        rs.getString("id"),
                        objectStorage.presignGet(rs.getString("storage_object_key"), false),
                        rs.getBoolean("is_primary"),
                        rs.getInt("display_order")),
                equipmentId);
    }

    private void lockEditableListing(String equipmentId, String subject) {
        List<String> rows = jdbcTemplate.query("""
                SELECT e.id FROM equipment e
                JOIN owner_profiles op ON op.id = e.owner_id
                JOIN users u ON u.id = op.user_id
                WHERE e.id = ? AND u.keycloak_subject_id = ?
                  AND op.verification_status = 'VERIFIED'
                  AND e.status IN ('DRAFT', 'REJECTED')
                FOR UPDATE
                """, (rs, row) -> rs.getString(1), equipmentId, subject);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Editable equipment listing not found.");
        }
    }

    private void requireOwnedListing(String equipmentId, String subject) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM equipment e
                JOIN owner_profiles op ON op.id = e.owner_id
                JOIN users u ON u.id = op.user_id
                WHERE e.id = ? AND u.keycloak_subject_id = ?
                  AND op.verification_status = 'VERIFIED'
                """, Integer.class, equipmentId, subject);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Equipment listing not found.");
        }
    }

    private ImageRow findImage(String equipmentId, String imageId) {
        return jdbcTemplate.query("""
                        SELECT storage_object_key, upload_expires_at, display_order
                        FROM equipment_images WHERE id = ? AND equipment_id = ? AND uploaded_at IS NULL
                        """,
                rs -> rs.next()
                        ? new ImageRow(rs.getString("storage_object_key"),
                        rs.getTimestamp("upload_expires_at") == null ? null
                                : rs.getTimestamp("upload_expires_at").toInstant(),
                        rs.getInt("display_order"))
                        : null,
                imageId, equipmentId);
    }

    private record ImageRow(String objectKey, Instant uploadExpiresAt, int displayOrder) {
    }
}
