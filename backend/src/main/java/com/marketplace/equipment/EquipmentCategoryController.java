package com.marketplace.equipment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class EquipmentCategoryController {

    private final EquipmentCatalogueService catalogueService;

    public EquipmentCategoryController(EquipmentCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    public List<EquipmentCategoryResponse> categories() {
        return catalogueService.activeCategories();
    }
}
