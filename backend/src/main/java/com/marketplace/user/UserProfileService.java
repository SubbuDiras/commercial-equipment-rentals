package com.marketplace.user;

import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserProfileService {

    private final JdbcTemplate jdbcTemplate;

    public UserProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String resolveOrCreate(Jwt jwt) {
        String subject = jwt.getSubject();
        String userId = findExistingOrNull(subject);
        if (userId != null) {
            return userId;
        }

        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ConflictException("The authenticated account must provide a subject and email address.");
        }

        String preferredName = jwt.getClaimAsString("preferred_username");
        String givenName = firstNonBlank(jwt.getClaimAsString("given_name"), preferredName, "Marketplace");
        String familyName = firstNonBlank(jwt.getClaimAsString("family_name"), "User");
        userId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    userId, subject, email, jwt.getClaimAsString("phone_number"), givenName, familyName);
            return userId;
        } catch (DataIntegrityViolationException exception) {
            String existingId = findExistingOrNull(subject);
            if (existingId != null) {
                return existingId;
            }
            throw new ConflictException("The authenticated account conflicts with an existing user profile.");
        }
    }

    public String requireExisting(String subject) {
        String userId = findExistingOrNull(subject);
        if (userId == null) {
            throw new ResourceNotFoundException("User profile not found.");
        }
        return userId;
    }

    public Optional<String> findExisting(String subject) {
        return Optional.ofNullable(findExistingOrNull(subject));
    }

    private String findExistingOrNull(String subject) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE keycloak_subject_id = ?", String.class, subject);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Marketplace";
    }
}
