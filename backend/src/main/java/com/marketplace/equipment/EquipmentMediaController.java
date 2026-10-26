package com.marketplace.equipment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EquipmentMediaController {

    private final EquipmentMediaService mediaService;

    public EquipmentMediaController(EquipmentMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/api/v1/owner/equipment/{equipmentId}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public EquipmentImageUploadResponse presign(
            @PathVariable String equipmentId,
            @Valid @RequestBody EquipmentImageUploadRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return mediaService.presign(equipmentId, request, jwt);
    }

    @PostMapping("/api/v1/owner/equipment/{equipmentId}/images/{imageId}/complete")
    @PreAuthorize("hasRole('OWNER')")
    public EquipmentImageResponse complete(
            @PathVariable String equipmentId,
            @PathVariable String imageId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return mediaService.complete(equipmentId, imageId, jwt);
    }

    @GetMapping("/api/v1/owner/equipment/{equipmentId}/images")
    @PreAuthorize("hasRole('OWNER')")
    public List<EquipmentImageResponse> ownerImages(
            @PathVariable String equipmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return mediaService.ownerImages(equipmentId, jwt);
    }

    @GetMapping("/api/v1/equipment/{equipmentId}/images")
    public List<EquipmentImageResponse> publicImages(@PathVariable String equipmentId) {
        return mediaService.publicImages(equipmentId);
    }
}
