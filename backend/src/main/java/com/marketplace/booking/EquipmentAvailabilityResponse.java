package com.marketplace.booking;

import java.time.Instant;

public record EquipmentAvailabilityResponse(
        String equipmentId,
        Instant startTime,
        Instant endTime,
        boolean available
) {
}
