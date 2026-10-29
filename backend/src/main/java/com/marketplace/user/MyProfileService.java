package com.marketplace.user;

import com.marketplace.exception.ConflictException;
import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The signed-in user's own profile: read it, and change their name and phone. */
@Service
public class MyProfileService {

    private static final Logger log = LoggerFactory.getLogger(MyProfileService.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final KeycloakAdminClient keycloakAdminClient;

    public MyProfileService(JdbcTemplate jdbcTemplate, UserProfileService userProfileService,
                            KeycloakAdminClient keycloakAdminClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.keycloakAdminClient = keycloakAdminClient;
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

    private MyProfileResponse read(String userId, Jwt jwt, Boolean signInNameUpdated) {
        return jdbcTemplate.queryForObject("""
                        SELECT first_name, last_name, email, phone, created_at FROM users WHERE id = ?
                        """,
                (rs, row) -> new MyProfileResponse(
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        roles(jwt),
                        rs.getTimestamp("created_at").toInstant(),
                        signInNameUpdated),
                userId);
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
