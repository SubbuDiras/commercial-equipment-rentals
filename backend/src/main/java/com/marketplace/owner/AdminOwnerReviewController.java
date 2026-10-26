package com.marketplace.owner;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/owners")
public class AdminOwnerReviewController {

    private final OwnerOnboardingService ownerOnboardingService;

    public AdminOwnerReviewController(OwnerOnboardingService ownerOnboardingService) {
        this.ownerOnboardingService = ownerOnboardingService;
    }

    @GetMapping
    public List<OwnerReviewResponse> list(
            @RequestParam(defaultValue = "UNDER_REVIEW") OwnerVerificationStatus status
    ) {
        return ownerOnboardingService.listForReview(status);
    }

    @PatchMapping("/{ownerProfileId}/approve")
    public OwnerReviewResponse approve(
            @PathVariable String ownerProfileId,
            @AuthenticationPrincipal Jwt adminJwt
    ) {
        return ownerOnboardingService.approve(ownerProfileId, adminJwt);
    }

    @PatchMapping("/{ownerProfileId}/reject")
    public OwnerReviewResponse reject(
            @PathVariable String ownerProfileId,
            @AuthenticationPrincipal Jwt adminJwt,
            @Valid @RequestBody OwnerRejectionRequest request
    ) {
        return ownerOnboardingService.reject(ownerProfileId, adminJwt, request.reason());
    }
}
