package com.marketplace.rental;

import com.marketplace.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {

    private final PaymentService paymentService;
    private final AdminDisputeService adminDisputeService;

    public AdminDisputeController(PaymentService paymentService, AdminDisputeService adminDisputeService) {
        this.paymentService = paymentService;
        this.adminDisputeService = adminDisputeService;
    }

    @GetMapping
    public List<DisputeSummaryResponse> list(@RequestParam(required = false) DisputeStatus status) {
        return adminDisputeService.list(status);
    }

    @GetMapping("/{disputeId}")
    public DisputeDetailResponse get(@PathVariable String disputeId) {
        return adminDisputeService.get(disputeId);
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
