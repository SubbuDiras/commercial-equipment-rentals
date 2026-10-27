package com.marketplace.rental;

import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDisputeService {

    private final JdbcTemplate jdbcTemplate;
    private final InspectionReportReader inspectionReportReader;

    public AdminDisputeService(JdbcTemplate jdbcTemplate, InspectionReportReader inspectionReportReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.inspectionReportReader = inspectionReportReader;
    }

    @Transactional(readOnly = true)
    public List<DisputeSummaryResponse> list(DisputeStatus status) {
        List<Object> parameters = new ArrayList<>();
        String filter = "";
        if (status != null) {
            filter = "WHERE d.status = ?";
            parameters.add(status.name());
        }
        return jdbcTemplate.query("""
                        SELECT d.id, d.booking_id, b.booking_reference, e.title, d.dispute_category,
                               d.claim_amount, b.security_deposit_amount, d.status,
                               u.first_name, u.last_name, d.created_at
                        FROM disputes d
                        JOIN bookings b ON b.id = d.booking_id
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN users u ON u.id = d.raised_by_user_id
                        %s
                        ORDER BY d.created_at ASC
                        """.formatted(filter),
                (resultSet, rowNumber) -> new DisputeSummaryResponse(
                        resultSet.getString("id"),
                        resultSet.getString("booking_id"),
                        resultSet.getString("booking_reference"),
                        resultSet.getString("title"),
                        resultSet.getString("dispute_category"),
                        resultSet.getBigDecimal("claim_amount"),
                        resultSet.getBigDecimal("security_deposit_amount"),
                        resultSet.getString("status"),
                        resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
                        resultSet.getTimestamp("created_at").toInstant()),
                parameters.toArray());
    }

    @Transactional(readOnly = true)
    public DisputeDetailResponse get(String disputeId) {
        DisputeDetailResponse dispute = jdbcTemplate.query("""
                        SELECT d.id, d.status, d.dispute_category, d.claim_amount, d.approved_deduction_amount,
                               d.description, d.resolution_notes, d.created_at, d.resolved_at,
                               b.id AS booking_id, b.booking_reference, b.status AS booking_status,
                               b.start_time, b.end_time, b.security_deposit_amount,
                               e.id AS equipment_id, e.title,
                               ou.first_name AS owner_first_name, ou.last_name AS owner_last_name,
                               ou.email AS owner_email,
                               ru.first_name AS renter_first_name, ru.last_name AS renter_last_name,
                               ru.email AS renter_email
                        FROM disputes d
                        JOIN bookings b ON b.id = d.booking_id
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN users ou ON ou.id = op.user_id
                        JOIN users ru ON ru.id = b.renter_user_id
                        WHERE d.id = ?
                        """,
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    Timestamp resolvedAt = resultSet.getTimestamp("resolved_at");
                    return new DisputeDetailResponse(
                            resultSet.getString("id"),
                            resultSet.getString("status"),
                            resultSet.getString("dispute_category"),
                            resultSet.getBigDecimal("claim_amount"),
                            resultSet.getBigDecimal("approved_deduction_amount"),
                            resultSet.getString("description"),
                            resultSet.getString("resolution_notes"),
                            resultSet.getTimestamp("created_at").toInstant(),
                            resolvedAt == null ? null : resolvedAt.toInstant(),
                            resultSet.getString("booking_id"),
                            resultSet.getString("booking_reference"),
                            resultSet.getString("booking_status"),
                            resultSet.getString("equipment_id"),
                            resultSet.getString("title"),
                            resultSet.getTimestamp("start_time").toInstant(),
                            resultSet.getTimestamp("end_time").toInstant(),
                            resultSet.getBigDecimal("security_deposit_amount"),
                            new DisputeDetailResponse.Party(
                                    resultSet.getString("owner_first_name") + " " + resultSet.getString("owner_last_name"),
                                    resultSet.getString("owner_email")),
                            new DisputeDetailResponse.Party(
                                    resultSet.getString("renter_first_name") + " " + resultSet.getString("renter_last_name"),
                                    resultSet.getString("renter_email")),
                            List.of());
                },
                disputeId);
        if (dispute == null) {
            throw new ResourceNotFoundException("Dispute not found.");
        }
        return dispute.withInspections(inspectionReportReader.forBooking(dispute.bookingId()));
    }
}
