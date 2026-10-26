package com.marketplace.equipment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEquipmentCategoryController {

    private final EquipmentCategoryAdminService categoryService;

    public AdminEquipmentCategoryController(EquipmentCategoryAdminService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEquipmentCategoryResponse create(
            @Valid @RequestBody EquipmentCategoryRequest request,
            @AuthenticationPrincipal Jwt adminJwt
    ) {
        return categoryService.create(request, adminJwt);
    }

    @PutMapping("/{categoryId}")
    public AdminEquipmentCategoryResponse update(
            @PathVariable String categoryId,
            @Valid @RequestBody EquipmentCategoryRequest request,
            @AuthenticationPrincipal Jwt adminJwt
    ) {
        return categoryService.update(categoryId, request, adminJwt);
    }
}
