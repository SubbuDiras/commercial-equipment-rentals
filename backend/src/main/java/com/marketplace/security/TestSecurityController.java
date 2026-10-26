package com.marketplace.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@Profile({"dev", "test"})
@Tag(name = "Security Verification", description = "Test endpoints to validate Keycloak JWT role-based access")
public class TestSecurityController {

    @GetMapping("/public")
    @Operation(summary = "Public endpoint", description = "Accessible without authentication.")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Public endpoint accessible to anyone"));
    }

    @GetMapping("/renter-only")
    @PreAuthorize("hasRole('RENTER')")
    @SecurityRequirement(name = "KeycloakAuth")
    @Operation(summary = "Renter-only endpoint", description = "Accessible only by users with ROLE_RENTER.")
    public ResponseEntity<Map<String, Object>> renterEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "message", "Renter access granted",
                "subject", jwt.getSubject()
        ));
    }

    @GetMapping("/owner-only")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "KeycloakAuth")
    @Operation(summary = "Owner-only endpoint", description = "Accessible only by users with ROLE_OWNER.")
    public ResponseEntity<Map<String, Object>> ownerEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "message", "Owner access granted",
                "subject", jwt.getSubject()
        ));
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "KeycloakAuth")
    @Operation(summary = "Admin-only endpoint", description = "Accessible only by users with ROLE_ADMIN.")
    public ResponseEntity<Map<String, Object>> adminEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "message", "Admin access granted",
                "subject", jwt.getSubject()
        ));
    }
}
