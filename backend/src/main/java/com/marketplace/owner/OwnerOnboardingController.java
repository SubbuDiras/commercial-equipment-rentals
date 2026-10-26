package com.marketplace.owner;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/onboarding")
public class OwnerOnboardingController {

    private final OwnerOnboardingService ownerOnboardingService;

    public OwnerOnboardingController(OwnerOnboardingService ownerOnboardingService) {
        this.ownerOnboardingService = ownerOnboardingService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RENTER', 'OWNER')")
    public ResponseEntity<OwnerProfileResponse> submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OwnerOnboardingRequest request
    ) {
        OwnerProfileResponse response = ownerOnboardingService.submit(jwt, request);
        return ResponseEntity.status(201).body(response);
    }
}
