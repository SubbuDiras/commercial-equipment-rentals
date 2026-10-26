package com.marketplace.equipment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EquipmentCatalogueService {

    private static final TypeReference<Map<String, Object>> SPECIFICATIONS_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EquipmentCatalogueService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EquipmentCategoryResponse> activeCategories() {
        List<CategoryRow> rows = jdbcTemplate.query("""
                        SELECT id, parent_category_id, slug, name, description
                        FROM equipment_categories
                        WHERE is_active = TRUE
                        ORDER BY name
                        """,
                (resultSet, rowNumber) -> new CategoryRow(
                        resultSet.getString("id"),
                        resultSet.getString("parent_category_id"),
                        resultSet.getString("slug"),
                        resultSet.getString("name"),
                        resultSet.getString("description")));

        Map<String, List<EquipmentCategoryResponse>> children = new LinkedHashMap<>();
        for (CategoryRow row : rows) {
            children.computeIfAbsent(row.id(), ignored -> new ArrayList<>());
        }
        List<EquipmentCategoryResponse> roots = new ArrayList<>();
        for (CategoryRow row : rows) {
            List<EquipmentCategoryResponse> subcategories =
                    children.computeIfAbsent(row.id(), ignored -> new ArrayList<>());
            EquipmentCategoryResponse category = new EquipmentCategoryResponse(
                    row.id(), row.slug(), row.name(), row.description(), subcategories);
            if (row.parentId() == null) {
                roots.add(category);
            } else {
                children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(category);
            }
        }
        return List.copyOf(roots);
    }

    @Transactional(readOnly = true)
    public EquipmentListingPage search(
            String categoryId,
            String city,
            BigDecimal minRate,
            BigDecimal maxRate,
            int page,
            int size
    ) {
        return search(categoryId, city, minRate, maxRate, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public EquipmentListingPage search(
            String categoryId,
            String city,
            BigDecimal minRate,
            BigDecimal maxRate,
            Instant startTime,
            Instant endTime,
            int page,
            int size
    ) {
        if (minRate != null && maxRate != null && minRate.compareTo(maxRate) > 0) {
            throw new BadRequestException("minRate must be less than or equal to maxRate.");
        }
        if ((startTime == null) != (endTime == null)) {
            throw new BadRequestException("startTime and endTime must be provided together.");
        }
        if (startTime != null && !startTime.isBefore(endTime)) {
            throw new BadRequestException("startTime must be before endTime.");
        }
        StringBuilder filters = new StringBuilder("""
                 e.status = 'ACTIVE'
                 AND op.verification_status = 'VERIFIED'
                 AND c.is_active = TRUE
                """);
        List<Object> arguments = new ArrayList<>();
        if (categoryId != null && !categoryId.isBlank()) {
            filters.append(" AND e.category_id = ?");
            arguments.add(categoryId);
        }
        if (city != null && !city.isBlank()) {
            filters.append(" AND LOWER(e.city) = LOWER(?)");
            arguments.add(city.trim());
        }
        if (minRate != null) {
            filters.append(" AND e.base_daily_rate >= ?");
            arguments.add(minRate);
        }
        if (maxRate != null) {
            filters.append(" AND e.base_daily_rate <= ?");
            arguments.add(maxRate);
        }
        if (startTime != null) {
            filters.append("""
                     AND NOT EXISTS (
                         SELECT 1 FROM bookings b
                         WHERE b.equipment_id = e.id
                           AND b.start_time < ? AND b.end_time > ?
                           AND (
                               b.status IN ('CONFIRMED', 'HANDOVER_PENDING', 'IN_PROGRESS',
                                            'RETURN_PENDING', 'RETURNED', 'DISPUTED', 'INSPECTION_PASSED')
                               OR (b.status IN ('REQUESTED', 'PAYMENT_PENDING', 'PAYMENT_FAILED')
                               AND b.hold_expires_at > CURRENT_TIMESTAMP(6))
                           )
                     )
                    """);
            arguments.add(Timestamp.from(endTime));
            arguments.add(Timestamp.from(startTime));
        }

        Long total = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE
                        """ + filters,
                Long.class, arguments.toArray());
        long totalElements = total == null ? 0L : total;
        long offsetValue = (long) page * size;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new BadRequestException("Requested page is too large.");
        }
        int offset = (int) offsetValue;
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(size);
        pageArguments.add(offset);
        List<EquipmentListingResponse> content = jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE
                        """ + filters + """
                        ORDER BY e.created_at DESC, e.id
                        LIMIT ? OFFSET ?
                        """,
                this::mapPublicListing, pageArguments.toArray());
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new EquipmentListingPage(content, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public EquipmentListingResponse getPublicListing(String equipmentId) {
        List<EquipmentListingResponse> rows = jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.id = ? AND e.status = 'ACTIVE'
                          AND op.verification_status = 'VERIFIED'
                          AND c.is_active = TRUE
                        """,
                this::mapPublicListing, equipmentId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Active equipment listing not found.");
        }
        return rows.get(0);
    }

    @Transactional
    public EquipmentListingResponse create(Jwt jwt, EquipmentListingRequest request) {
        validateFulfillmentOptions(request);
        String ownerId = verifiedOwnerId(jwt);
        requireActiveCategory(request.categoryId());
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO equipment (
                    id, owner_id, category_id, title, description, manufacturer, model_number,
                    serial_number, year_of_manufacture, condition_grade, specifications_json,
                    base_daily_rate, base_weekly_rate, security_deposit, minimum_rental_days,
                    delivery_supported, pickup_supported, delivery_charge_flat, address_line,
                    city, postal_code, latitude, longitude, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')
                """,
                id, ownerId, request.categoryId(), request.title().trim(), request.description().trim(),
                request.manufacturer().trim(), request.modelNumber().trim(), emptyToNull(request.serialNumber()),
                request.yearOfManufacture(), request.conditionGrade(), serializeSpecifications(request.specifications()),
                request.baseDailyRate(), request.baseWeeklyRate(), request.securityDeposit(),
                request.minimumRentalDays(), request.deliverySupported(), request.pickupSupported(),
                request.deliveryChargeFlat(), request.addressLine().trim(), request.city().trim(),
                request.postalCode().trim(), request.latitude(), request.longitude());
        return findOwnerListing(id, ownerId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentListingResponse> ownListings(Jwt jwt) {
        String ownerId = verifiedOwnerId(jwt);
        return jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.owner_id = ?
                        ORDER BY e.created_at DESC
                        """,
                this::mapListing, ownerId);
    }

    @Transactional
    public EquipmentListingResponse update(Jwt jwt, String equipmentId, EquipmentListingRequest request) {
        validateFulfillmentOptions(request);
        String ownerId = verifiedOwnerId(jwt);
        requireActiveCategory(request.categoryId());
        int updated = jdbcTemplate.update("""
                UPDATE equipment SET
                    category_id = ?, title = ?, description = ?, manufacturer = ?, model_number = ?,
                    serial_number = ?, year_of_manufacture = ?, condition_grade = ?, specifications_json = ?,
                    base_daily_rate = ?, base_weekly_rate = ?, security_deposit = ?, minimum_rental_days = ?,
                    delivery_supported = ?, pickup_supported = ?, delivery_charge_flat = ?, address_line = ?,
                    city = ?, postal_code = ?, latitude = ?, longitude = ?, version = version + 1
                WHERE id = ? AND owner_id = ? AND status IN ('DRAFT', 'REJECTED')
                """,
                request.categoryId(), request.title().trim(), request.description().trim(),
                request.manufacturer().trim(), request.modelNumber().trim(), emptyToNull(request.serialNumber()),
                request.yearOfManufacture(), request.conditionGrade(), serializeSpecifications(request.specifications()),
                request.baseDailyRate(), request.baseWeeklyRate(), request.securityDeposit(),
                request.minimumRentalDays(), request.deliverySupported(), request.pickupSupported(),
                request.deliveryChargeFlat(), request.addressLine().trim(), request.city().trim(),
                request.postalCode().trim(), request.latitude(), request.longitude(), equipmentId, ownerId);
        if (updated != 1) {
            assertOwned(equipmentId, ownerId);
            throw new ConflictException("Only draft or rejected listings can be edited.");
        }
        return findOwnerListing(equipmentId, ownerId);
    }

    @Transactional
    public EquipmentListingResponse submit(Jwt jwt, String equipmentId) {
        String ownerId = verifiedOwnerId(jwt);
        int updated = jdbcTemplate.update("""
                UPDATE equipment SET status = 'PENDING_APPROVAL', version = version + 1
                WHERE id = ? AND owner_id = ? AND status IN ('DRAFT', 'REJECTED')
                """,
                equipmentId, ownerId);
        if (updated != 1) {
            assertOwned(equipmentId, ownerId);
            throw new ConflictException("Only draft or rejected listings can be submitted for approval.");
        }
        return findOwnerListing(equipmentId, ownerId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentListingResponse> pendingListings() {
        return jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.status = 'PENDING_APPROVAL'
                        ORDER BY e.created_at
                        """,
                this::mapListing);
    }

    @Transactional
    public EquipmentListingResponse review(String equipmentId, Jwt adminJwt, boolean approve, String reason) {
        if (!approve && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A rejection reason is required.");
        }
        String targetStatus = approve ? "ACTIVE" : "REJECTED";
        int updated = jdbcTemplate.update("""
                UPDATE equipment SET status = ?, version = version + 1
                WHERE id = ? AND status = 'PENDING_APPROVAL'
                  AND owner_id IN (
                      SELECT id FROM owner_profiles WHERE verification_status = 'VERIFIED'
                  )
                """,
                targetStatus, equipmentId);
        if (updated != 1) {
            if (!exists(equipmentId)) {
                throw new ResourceNotFoundException("Equipment listing not found.");
            }
            throw new ConflictException("Only listings pending approval can be reviewed.");
        }
        writeAudit(adminJwt, approve ? "EQUIPMENT_LISTING_APPROVED" : "EQUIPMENT_LISTING_REJECTED",
                equipmentId, approve ? Map.of("status", targetStatus)
                        : Map.of("status", targetStatus, "reason", reason.trim()));
        return findAnyListing(equipmentId);
    }

    private String verifiedOwnerId(Jwt jwt) {
        List<String> owners = jdbcTemplate.query("""
                        SELECT op.id
                        FROM owner_profiles op
                        JOIN users u ON u.id = op.user_id
                        WHERE u.keycloak_subject_id = ? AND op.verification_status = 'VERIFIED'
                        """,
                (resultSet, rowNumber) -> resultSet.getString(1), jwt.getSubject());
        if (owners.isEmpty()) {
            throw new AccessDeniedException("A verified owner profile is required to manage listings.");
        }
        return owners.get(0);
    }

    private void validateFulfillmentOptions(EquipmentListingRequest request) {
        if (!request.deliverySupported() && !request.pickupSupported()) {
            throw new BadRequestException("A listing must support pickup, delivery, or both.");
        }
        if (!request.deliverySupported() && request.deliveryChargeFlat().signum() > 0) {
            throw new BadRequestException("A delivery charge cannot be set when delivery is not offered.");
        }
    }

    private void requireActiveCategory(String categoryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_categories WHERE id = ? AND is_active = TRUE",
                Integer.class, categoryId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Active equipment category not found.");
        }
    }

    private EquipmentListingResponse findOwnerListing(String id, String ownerId) {
        List<EquipmentListingResponse> rows = jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.id = ? AND e.owner_id = ?
                        """,
                this::mapListing, id, ownerId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Equipment listing not found.");
        }
        return rows.get(0);
    }

    private EquipmentListingResponse findAnyListing(String id) {
        List<EquipmentListingResponse> rows = jdbcTemplate.query("""
                        SELECT e.*, c.name AS category_name
                        FROM equipment e JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.id = ?
                        """,
                this::mapListing, id);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Equipment listing not found.");
        }
        return rows.get(0);
    }

    private void assertOwned(String equipmentId, String ownerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment WHERE id = ? AND owner_id = ?",
                Integer.class, equipmentId, ownerId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Equipment listing not found.");
        }
    }

    private boolean exists(String equipmentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment WHERE id = ?", Integer.class, equipmentId);
        return count != null && count > 0;
    }

    private void writeAudit(Jwt adminJwt, String action, String equipmentId, Map<String, String> details) {
        String actorId = jdbcTemplate.query("""
                        SELECT id FROM users WHERE keycloak_subject_id = ?
                        """,
                resultSet -> resultSet.next() ? resultSet.getString(1) : null, adminJwt.getSubject());
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                    VALUES (?, ?, ?, 'equipment', ?, ?)
                    """,
                    UUID.randomUUID().toString(), actorId, action, equipmentId,
                    objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize listing review audit details.", exception);
        }
    }

    private String serializeSpecifications(Map<String, Object> specifications) {
        if (specifications == null || specifications.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(specifications);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Listing specifications could not be serialized.", exception);
        }
    }

    private Map<String, Object> deserializeSpecifications(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SPECIFICATIONS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored listing specifications contain invalid JSON.", exception);
        }
    }

    private EquipmentListingResponse mapListing(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        int year = resultSet.getInt("year_of_manufacture");
        Integer yearOfManufacture = resultSet.wasNull() ? null : year;
        return new EquipmentListingResponse(
                resultSet.getString("id"),
                resultSet.getString("category_id"),
                resultSet.getString("category_name"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("manufacturer"),
                resultSet.getString("model_number"),
                resultSet.getString("serial_number"),
                yearOfManufacture,
                resultSet.getString("condition_grade"),
                deserializeSpecifications(resultSet.getString("specifications_json")),
                resultSet.getBigDecimal("base_daily_rate"),
                resultSet.getBigDecimal("base_weekly_rate"),
                resultSet.getBigDecimal("security_deposit"),
                resultSet.getInt("minimum_rental_days"),
                resultSet.getBoolean("delivery_supported"),
                resultSet.getBoolean("pickup_supported"),
                resultSet.getBigDecimal("delivery_charge_flat"),
                resultSet.getString("address_line"),
                resultSet.getString("city"),
                resultSet.getString("postal_code"),
                resultSet.getBigDecimal("latitude"),
                resultSet.getBigDecimal("longitude"),
                resultSet.getString("status"),
                createdAt == null ? null : createdAt.toInstant());
    }

    private EquipmentListingResponse mapPublicListing(ResultSet resultSet, int rowNumber) throws SQLException {
        EquipmentListingResponse listing = mapListing(resultSet, rowNumber);
        return new EquipmentListingResponse(
                listing.id(),
                listing.categoryId(),
                listing.categoryName(),
                listing.title(),
                listing.description(),
                listing.manufacturer(),
                listing.modelNumber(),
                null,
                listing.yearOfManufacture(),
                listing.conditionGrade(),
                listing.specifications(),
                listing.baseDailyRate(),
                listing.baseWeeklyRate(),
                listing.securityDeposit(),
                listing.minimumRentalDays(),
                listing.deliverySupported(),
                listing.pickupSupported(),
                listing.deliveryChargeFlat(),
                null,
                listing.city(),
                null,
                null,
                null,
                listing.status(),
                listing.createdAt());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CategoryRow(String id, String parentId, String slug, String name, String description) {
    }
}
