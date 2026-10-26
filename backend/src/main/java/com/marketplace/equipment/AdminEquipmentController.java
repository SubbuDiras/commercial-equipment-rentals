package com.marketplace.equipment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/listings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEquipmentController {

    private final EquipmentCatalogueService catalogueService;

    public AdminEquipmentController(EquipmentCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping("/pending")
    public List<EquipmentListingResponse> pending() {
        return catalogueService.pendingListings();
    }

    @PatchMapping("/{equipmentId}/approve")
    public EquipmentListingResponse approve(
            @PathVariable String equipmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return catalogueService.review(equipmentId, jwt, true, null);
    }

    @PatchMapping("/{equipmentId}/reject")
    public EquipmentListingResponse reject(
            @PathVariable String equipmentId,
            @RequestParam String reason,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return catalogueService.review(equipmentId, jwt, false, reason);
    }
}
