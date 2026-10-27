package com.marketplace.rental;

import com.marketplace.payment.PaymentService;
import com.marketplace.review.ReviewService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RentalOperationsTask {

    private final RentalInspectionService inspectionService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;

    public RentalOperationsTask(
            RentalInspectionService inspectionService,
            PaymentService paymentService,
            ReviewService reviewService
    ) {
        this.inspectionService = inspectionService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
    }

    @Scheduled(fixedDelayString = "${rental.operations.sweep-interval-ms:60000}")
    public void advanceRentalStates() {
        inspectionService.advanceTimedStates();
        if (paymentService.financialOperationsEnabled()) {
            paymentService.releaseMaturedSettlements();
        }
        reviewService.closeExpiredReviewWindows();
    }
}
