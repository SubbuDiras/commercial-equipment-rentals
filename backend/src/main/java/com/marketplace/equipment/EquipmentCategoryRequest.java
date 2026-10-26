package com.marketplace.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record EquipmentCategoryRequest(
        String parentCategoryId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 10000) String description,
        @NotNull Boolean active
) {
}
