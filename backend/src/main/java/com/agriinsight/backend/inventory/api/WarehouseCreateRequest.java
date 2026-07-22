package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.domain.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record WarehouseCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 160) String displayName,
        @Size(max = 240) String locationText,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public WarehouseCreateRequest {
        code = code == null ? null : Warehouse.canonicalCode(code);
        displayName = displayName == null ? null : Warehouse.canonicalDisplayName(displayName);
        locationText = locationText == null ? null : Warehouse.canonicalLocation(locationText);
        reasonCode = normalizeReason(reasonCode);
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank()
                ? null
                : value.strip().toUpperCase(Locale.ROOT);
    }
}
