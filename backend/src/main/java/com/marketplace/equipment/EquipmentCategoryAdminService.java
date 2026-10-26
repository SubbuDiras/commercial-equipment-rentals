package com.marketplace.equipment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EquipmentCategoryAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EquipmentCategoryAdminService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminEquipmentCategoryResponse create(EquipmentCategoryRequest request, Jwt adminJwt) {
        validateParent(request.parentCategoryId(), null);
        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO equipment_categories
                        (id, parent_category_id, slug, name, description, is_active)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id, request.parentCategoryId(), request.slug(), request.name().trim(),
                    request.description(), request.active());
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("A category with this slug already exists.");
        }
        writeAudit(adminJwt, "EQUIPMENT_CATEGORY_CREATED", id);
        return get(id);
    }

    @Transactional
    public AdminEquipmentCategoryResponse update(
            String categoryId,
            EquipmentCategoryRequest request,
            Jwt adminJwt
    ) {
        get(categoryId);
        validateParent(request.parentCategoryId(), categoryId);
        if (!request.active()) {
            Integer activeChildren = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM equipment_categories
                    WHERE parent_category_id = ? AND is_active = TRUE
                    """, Integer.class, categoryId);
            if (activeChildren != null && activeChildren > 0) {
                throw new ConflictException("Deactivate child categories before deactivating their parent.");
            }
        }
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE equipment_categories
                    SET parent_category_id = ?, slug = ?, name = ?, description = ?, is_active = ?
                    WHERE id = ?
                    """,
                    request.parentCategoryId(), request.slug(), request.name().trim(),
                    request.description(), request.active(), categoryId);
            if (updated != 1) {
                throw new ResourceNotFoundException("Equipment category not found.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("A category with this slug already exists.");
        }
        writeAudit(adminJwt, "EQUIPMENT_CATEGORY_UPDATED", categoryId);
        return get(categoryId);
    }

    private AdminEquipmentCategoryResponse get(String categoryId) {
        List<AdminEquipmentCategoryResponse> rows = jdbcTemplate.query("""
                        SELECT id, parent_category_id, slug, name, description, is_active
                        FROM equipment_categories WHERE id = ?
                        """,
                (rs, row) -> new AdminEquipmentCategoryResponse(rs.getString("id"),
                        rs.getString("parent_category_id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("is_active")),
                categoryId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Equipment category not found.");
        }
        return rows.get(0);
    }

    private void validateParent(String parentId, String categoryId) {
        if (parentId == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        String currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(categoryId) || !visited.add(currentId)) {
                throw new ConflictException("Equipment categories cannot be their own ancestor.");
            }
            String nextId = jdbcTemplate.query("""
                            SELECT parent_category_id FROM equipment_categories
                            WHERE id = ? AND is_active = TRUE
                            """,
                    rs -> rs.next() ? rs.getString(1) : null,
                    currentId);
            if (nextId == null && !existsActiveCategory(currentId)) {
                throw new ResourceNotFoundException("Active parent equipment category not found.");
            }
            currentId = nextId;
        }
    }

    private boolean existsActiveCategory(String categoryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_categories WHERE id = ? AND is_active = TRUE",
                Integer.class, categoryId);
        return count != null && count > 0;
    }

    private void writeAudit(Jwt adminJwt, String action, String categoryId) {
        String actorId = jdbcTemplate.query("""
                        SELECT id FROM users WHERE keycloak_subject_id = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                adminJwt.getSubject());
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                    VALUES (?, ?, ?, 'equipment_categories', ?, ?)
                    """,
                    UUID.randomUUID().toString(), actorId, action, categoryId,
                    objectMapper.writeValueAsString(Map.of("adminSubject", adminJwt.getSubject())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize category audit details.", exception);
        }
    }
}
