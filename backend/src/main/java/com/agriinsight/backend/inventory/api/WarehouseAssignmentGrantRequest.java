package com.agriinsight.backend.inventory.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.UUID;

public record WarehouseAssignmentGrantRequest(
        @NotNull UUID userProfileId,
        @NotNull UUID warehouseId,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public WarehouseAssignmentGrantRequest {
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? null
                : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
