package com.marketplace.rental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.storage.PrivateObjectStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads submitted handover/return inspections with short-lived inline photo URLs.
 * Callers are responsible for checking that the viewer may see the booking.
 */
@Component
public class InspectionReportReader {

    private final JdbcTemplate jdbcTemplate;
    private final PrivateObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    public InspectionReportReader(JdbcTemplate jdbcTemplate, PrivateObjectStorage objectStorage, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
    }

    public List<InspectionReport> forBooking(String bookingId) {
        Map<String, List<InspectionReport.Photo>> photosByInspection = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT rim.inspection_id, rim.storage_object_key, rim.media_tag, rim.captured_at
                        FROM rental_inspection_media rim
                        JOIN rental_inspections ri ON ri.id = rim.inspection_id
                        WHERE ri.booking_id = ?
                        ORDER BY rim.captured_at, rim.id
                        """,
                resultSet -> {
                    photosByInspection.computeIfAbsent(resultSet.getString("inspection_id"), id -> new ArrayList<>())
                            .add(new InspectionReport.Photo(
                                    resultSet.getString("media_tag"),
                                    objectStorage.presignGet(resultSet.getString("storage_object_key"), false),
                                    resultSet.getTimestamp("captured_at").toInstant()));
                },
                bookingId);
        return jdbcTemplate.query("""
                        SELECT id, inspection_type, inspected_at, hour_meter_reading, fuel_or_battery_level,
                               condition_summary, checklist_json
                        FROM rental_inspections
                        WHERE booking_id = ?
                        ORDER BY inspected_at
                        """,
                (resultSet, rowNumber) -> new InspectionReport(
                        resultSet.getString("id"),
                        resultSet.getString("inspection_type"),
                        resultSet.getTimestamp("inspected_at").toInstant(),
                        resultSet.getBigDecimal("hour_meter_reading"),
                        resultSet.getString("fuel_or_battery_level"),
                        resultSet.getString("condition_summary"),
                        parseChecklist(resultSet.getString("checklist_json")),
                        photosByInspection.getOrDefault(resultSet.getString("id"), List.of())),
                bookingId);
    }

    private Map<String, Boolean> parseChecklist(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Boolean>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored inspection checklist is not valid JSON.", exception);
        }
    }
}
