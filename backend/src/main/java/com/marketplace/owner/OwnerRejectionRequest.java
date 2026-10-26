package com.marketplace.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerRejectionRequest(
        @NotBlank
        @Size(max = 2000)
        String reason
) {
}
