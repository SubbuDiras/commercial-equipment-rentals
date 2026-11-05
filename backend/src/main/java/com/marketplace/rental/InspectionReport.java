package com.marketplace.rental;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InspectionReport(
        String inspectionId,
        String type,
        Instant inspectedAt,
        BigDecimal hourMeterReading,
        String fuelOrBatteryLevel,
        String conditionSummary,
        Map<String, Boolean> checklist,
        List<Photo> photos
) {
    public record Photo(String mediaTag, String url, Instant capturedAt) {
    }
}
