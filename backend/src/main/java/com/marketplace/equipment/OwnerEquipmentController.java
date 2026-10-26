package com.marketplace.equipment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/owner/equipment")
@PreAuthorize("hasRole('OWNER')")
public class OwnerEquipmentController {

    private final EquipmentCatalogueService catalogueService;

    public OwnerEquipmentController(EquipmentCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentListingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EquipmentListingRequest request
    ) {
        return catalogueService.create(jwt, request);
    }

    @GetMapping
    public List<EquipmentListingResponse> ownListings(@AuthenticationPrincipal Jwt jwt) {
        return catalogueService.ownListings(jwt);
    }

    @PutMapping("/{equipmentId}")
    public EquipmentListingResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String equipmentId,
            @Valid @RequestBody EquipmentListingRequest request
    ) {
        return catalogueService.update(jwt, equipmentId, request);
    }

    @PostMapping("/{equipmentId}/submit")
    public EquipmentListingResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String equipmentId
    ) {
        return catalogueService.submit(jwt, equipmentId);
    }
}
