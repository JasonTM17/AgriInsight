package com.agriinsight.backend.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WarehouseLifecycleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public WarehouseLifecycleRequest {
        reasonCode = WarehouseCreateRequest.normalizeReason(reasonCode);
    }
}
