package com.agriinsight.backend.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record WarehouseAssignmentRevokeRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public WarehouseAssignmentRevokeRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
