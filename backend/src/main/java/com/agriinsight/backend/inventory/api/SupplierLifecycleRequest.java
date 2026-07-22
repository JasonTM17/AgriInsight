package com.agriinsight.backend.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SupplierLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public SupplierLifecycleRequest {
        reasonCode = SupplierCreateRequest.normalizeReason(reasonCode);
    }
}
