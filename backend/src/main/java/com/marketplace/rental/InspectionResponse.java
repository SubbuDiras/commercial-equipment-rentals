package com.marketplace.rental;

import java.time.Instant;

public record InspectionResponse(
        String inspectionId,
        String bookingId,
        String type,
        String bookingStatus,
        Instant inspectedAt,
        Instant disputeDeadline
) {
}
