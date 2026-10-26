package com.marketplace.rental;

import com.marketplace.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {

    private final PaymentService paymentService;

    public AdminDisputeController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PatchMapping("/{disputeId}/resolve")
    public void resolve(
            @PathVariable String disputeId,
            @Valid @RequestBody DisputeResolutionRequest request,
            @AuthenticationPrincipal Jwt adminJwt
    ) {
        paymentService.resolveDispute(disputeId, request, adminJwt);
    }
}
