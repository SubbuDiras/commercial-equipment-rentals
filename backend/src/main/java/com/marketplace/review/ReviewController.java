package com.marketplace.review;

import com.marketplace.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/v1/bookings/{bookingId}/reviews")
    @PreAuthorize("hasAnyRole('RENTER', 'OWNER')")
    public ResponseEntity<ReviewResponse> submit(
            @PathVariable String bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.submit(bookingId, jwt, request));
    }

    /** Public: covered by the {@code GET /api/v1/equipment/**} permit-all rule. */
    @GetMapping("/api/v1/equipment/{equipmentId}/reviews")
    public PageResponse<EquipmentReviewResponse> equipmentReviews(
            @PathVariable String equipmentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return reviewService.equipmentReviews(equipmentId, page, size);
    }
}
