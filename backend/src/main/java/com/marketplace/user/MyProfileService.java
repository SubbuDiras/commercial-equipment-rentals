package com.marketplace.user;

import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import com.marketplace.storage.PrivateObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in user's own profile: read it, change their name and phone, and set or remove their photo. */
@Service
public class MyProfileService {

    private static final Logger log = LoggerFactory.getLogger(MyProfileService.class);
    static final long MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final PrivateObjectStorage storage;

    public MyProfileService(JdbcTemplate jdbcTemplate, UserProfileService userProfileService,
                            KeycloakAdminClient keycloakAdminClient, PrivateObjectStorage storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.storage = storage;
    }

    @Transactional
    public MyProfileResponse get(Jwt jwt) {
        return read(userProfileService.resolveOrCreate(jwt), jwt, null);
    }

    /**
     * Saves the new name and phone, then copies the name to Keycloak so the header and emails use it after the next
     * token refresh. The Keycloak copy is best-effort: if it fails, the marketplace still keeps the change.
     */
    @Transactional
    public MyProfileResponse update(Jwt jwt, MyProfileUpdateRequest request) {
        String userId = userProfileService.resolveOrCreate(jwt);
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        try {
            jdbcTemplate.update("UPDATE users SET first_name = ?, last_name = ?, phone = ? WHERE id = ?",
                    firstName, lastName, phone, userId);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("This phone number is already registered to another account. Use a different phone number.");
        }
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                VALUES (?, ?, 'USER_PROFILE_UPDATED', 'users', ?, ?)
                """, UUID.randomUUID().toString(), userId, userId, "{\"phoneSet\":" + (phone != null) + "}");

        boolean signInNameUpdated;
        try {
            keycloakAdminClient.updateUserName(jwt.getSubject(), firstName, lastName);
            signInNameUpdated = true;
        } catch (KeycloakAdminException exception) {
            log.warn("Profile name saved, but not copied to Keycloak for user {}: {}", userId, exception.getMessage());
            signInNameUpdated = false;
        }
        return read(userId, jwt, signInNameUpdated);
    }

    /**
     * Replaces the profile photo. The browser crops it to a small square first, so 2 MB is plenty; the bytes must
     * really be a JPEG, PNG or WebP image, whatever the declared type, because the photo is shown back to people.
     */
    @Transactional
    public MyProfileResponse updatePhoto(Jwt jwt, MultipartFile photo) {
        String userId = userProfileService.resolveOrCreate(jwt);
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES) {
            throw new BadRequestException("Choose a JPG, PNG or WebP photo no larger than 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("The photo could not be read. Please try again.");
        }
        String extension = imageExtension(bytes);
        if (extension == null) {
            throw new BadRequestException("Choose a JPG, PNG or WebP photo no larger than 2 MB.");
        }
        String key = "users/" + userId + "/profile-" + UUID.randomUUID() + "." + extension;
        storage.put(key, "image/" + extension, bytes);
        String previous = replacePhotoKey(userId, key);
        audit(userId, "USER_PHOTO_UPDATED");
        deleteQuietly(previous);
        return read(userId, jwt, null);
    }

    @Transactional
    public MyProfileResponse removePhoto(Jwt jwt) {
        String userId = userProfileService.resolveOrCreate(jwt);
        String previous = replacePhotoKey(userId, null);
        if (previous != null) {
            audit(userId, "USER_PHOTO_REMOVED");
        }
        deleteQuietly(previous);
        return read(userId, jwt, null);
    }

    private String replacePhotoKey(String userId, String key) {
        String previous = jdbcTemplate.queryForObject(
                "SELECT profile_photo_key FROM users WHERE id = ? FOR UPDATE", String.class, userId);
        jdbcTemplate.update("UPDATE users SET profile_photo_key = ? WHERE id = ?", key, userId);
        return previous;
    }

    private void audit(String userId, String action) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                VALUES (?, ?, ?, 'users', ?, '{}')
                """, UUID.randomUUID().toString(), userId, action, userId);
    }

    /** The old photo is no longer referenced; if deleting it fails, it is only an orphaned file. */
    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Old profile photo {} could not be deleted: {}", key, exception.getMessage());
        }
    }

    /** jpeg, png or webp from the file's first bytes, or null for anything else. */
    static String imageExtension(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private MyProfileResponse read(String userId, Jwt jwt, Boolean signInNameUpdated) {
        return jdbcTemplate.queryForObject("""
                        SELECT first_name, last_name, email, phone, created_at, profile_photo_key FROM users WHERE id = ?
                        """,
                (rs, row) -> new MyProfileResponse(
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        roles(jwt),
                        rs.getTimestamp("created_at").toInstant(),
                        signInNameUpdated,
                        photoUrl(rs.getString("profile_photo_key"))),
                userId);
    }

    /** A broken storage link should not break the whole profile page; the photo is then just not shown. */
    private String photoUrl(String key) {
        if (key == null) {
            return null;
        }
        try {
            return storage.presignGet(key, false);
        } catch (RuntimeException exception) {
            log.warn("Profile photo {} could not be linked: {}", key, exception.getMessage());
            return null;
        }
    }

    private static List<String> roles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> role.startsWith("ROLE_"))
                .sorted()
                .toList();
    }
}
