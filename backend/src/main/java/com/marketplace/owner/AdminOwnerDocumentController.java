package com.marketplace.owner;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/owners/{ownerProfileId}/documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOwnerDocumentController {

    private final OwnerDocumentService documentService;

    public AdminOwnerDocumentController(OwnerDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<OwnerDocumentResponse> list(@PathVariable String ownerProfileId) {
        return documentService.adminDocuments(ownerProfileId);
    }

    @GetMapping("/{documentId}/download")
    public OwnerDocumentController.OwnerDocumentDownloadResponse download(
            @PathVariable String ownerProfileId,
            @PathVariable String documentId,
            /** true: a link the browser shows in the page (the review preview) instead of saving as a file. */
            @RequestParam(defaultValue = "false") boolean inline,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new OwnerDocumentController.OwnerDocumentDownloadResponse(
                documentService.adminDownloadUrl(ownerProfileId, documentId, jwt, inline));
    }
}
