package com.marketplace.rental;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/rentals/{bookingId}")
@PreAuthorize("hasRole('OWNER')")
public class OwnerRentalController {

    private final RentalInspectionService rentalInspectionService;

    public OwnerRentalController(RentalInspectionService rentalInspectionService) {
        this.rentalInspectionService = rentalInspectionService;
    }

    @PostMapping("/approve-condition")
    public ConditionApprovalResponse approveCondition(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.approveCondition(bookingId, jwt);
    }
}
