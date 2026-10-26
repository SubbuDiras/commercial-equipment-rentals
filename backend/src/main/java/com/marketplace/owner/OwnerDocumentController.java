package com.marketplace.owner;

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
@RequestMapping("/api/v1/owner/onboarding/{ownerProfileId}/documents")
@PreAuthorize("hasAnyRole('RENTER', 'OWNER')")
public class OwnerDocumentController {

    private final OwnerDocumentService documentService;

    public OwnerDocumentController(OwnerDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerDocumentUploadResponse presign(
            @PathVariable String ownerProfileId,
            @Valid @RequestBody OwnerDocumentUploadRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return documentService.presign(ownerProfileId, request, jwt);
    }

    @PostMapping("/{documentId}/complete")
    public OwnerDocumentResponse complete(
            @PathVariable String ownerProfileId,
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return documentService.complete(ownerProfileId, documentId, jwt);
    }

    @GetMapping
    public List<OwnerDocumentResponse> list(
            @PathVariable String ownerProfileId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return documentService.ownerDocuments(ownerProfileId, jwt);
    }

    @GetMapping("/{documentId}/download")
    public OwnerDocumentDownloadResponse download(
            @PathVariable String ownerProfileId,
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new OwnerDocumentDownloadResponse(
                documentService.ownerDownloadUrl(ownerProfileId, documentId, jwt));
    }

    public record OwnerDocumentDownloadResponse(String downloadUrl) {
    }
}
