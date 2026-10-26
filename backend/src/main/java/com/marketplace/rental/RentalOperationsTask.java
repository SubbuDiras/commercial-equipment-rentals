package com.marketplace.rental;

import com.marketplace.payment.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RentalOperationsTask {

    private final RentalInspectionService inspectionService;
    private final PaymentService paymentService;

    public RentalOperationsTask(
            RentalInspectionService inspectionService,
            PaymentService paymentService
    ) {
        this.inspectionService = inspectionService;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${rental.operations.sweep-interval-ms:60000}")
    public void advanceRentalStates() {
        inspectionService.advanceTimedStates();
        if (paymentService.financialOperationsEnabled()) {
            paymentService.releaseMaturedSettlements();
        }
    }
}
