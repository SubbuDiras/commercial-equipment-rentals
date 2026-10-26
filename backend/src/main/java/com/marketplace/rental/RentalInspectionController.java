package com.marketplace.rental;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rentals/{bookingId}")
@PreAuthorize("hasAnyRole('OWNER', 'RENTER')")
public class RentalInspectionController {

    private final RentalInspectionService rentalInspectionService;

    public RentalInspectionController(RentalInspectionService rentalInspectionService) {
        this.rentalInspectionService = rentalInspectionService;
    }

    @PostMapping("/otp")
    public OtpChallengeResponse issueOtp(
            @PathVariable String bookingId,
            @RequestParam InspectionType type,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.issueOtp(bookingId, type, jwt);
    }

    @PostMapping("/inspection-uploads")
    public InspectionUploadResponse presignUpload(
            @PathVariable String bookingId,
            @RequestParam InspectionType type,
            @Valid @RequestBody InspectionUploadRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.presignPhoto(
                bookingId, type, request.contentType(), request.contentLength(), jwt);
    }

    @PostMapping("/handover")
    public InspectionResponse handover(
            @PathVariable String bookingId,
            @Valid @RequestBody InspectionSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.submit(bookingId, InspectionType.HANDOVER, request, jwt);
    }

    @PostMapping("/return")
    public InspectionResponse returnEquipment(
            @PathVariable String bookingId,
            @Valid @RequestBody InspectionSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.submit(bookingId, InspectionType.RETURN, request, jwt);
    }

    @PostMapping("/disputes")
    public String raiseDamageClaim(
            @PathVariable String bookingId,
            @Valid @RequestBody DamageClaimRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return rentalInspectionService.raiseDamageClaim(bookingId, request, jwt);
    }
}
