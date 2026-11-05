package com.marketplace.rental;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DisputeDetailResponse(
        String disputeId,
        String status,
        String category,
        BigDecimal claimAmount,
        BigDecimal approvedDeductionAmount,
        String description,
        String resolutionNotes,
        Instant createdAt,
        Instant resolvedAt,
        String bookingId,
        String bookingReference,
        String bookingStatus,
        String equipmentId,
        String equipmentTitle,
        Instant startTime,
        Instant endTime,
        BigDecimal securityDeposit,
        Party owner,
        Party renter,
        List<InspectionReport> inspections
) {
    DisputeDetailResponse withInspections(List<InspectionReport> reports) {
        return new DisputeDetailResponse(disputeId, status, category, claimAmount, approvedDeductionAmount,
                description, resolutionNotes, createdAt, resolvedAt, bookingId, bookingReference, bookingStatus,
                equipmentId, equipmentTitle, startTime, endTime, securityDeposit, owner, renter, reports);
    }

    public record Party(String name, String email) {
    }
}
