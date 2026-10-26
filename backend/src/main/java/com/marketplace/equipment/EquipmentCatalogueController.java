package com.marketplace.equipment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentCatalogueController {

    private final EquipmentCatalogueService catalogueService;

    public EquipmentCatalogueController(EquipmentCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    public EquipmentListingPage search(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @Size(max = 100) String city,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minRate,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxRate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return catalogueService.search(categoryId, city, minRate, maxRate, startTime, endTime, page, size);
    }

    @GetMapping("/{equipmentId}")
    public EquipmentListingResponse get(@PathVariable String equipmentId) {
        return catalogueService.getPublicListing(equipmentId);
    }
}
