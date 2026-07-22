package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.domain.Warehouse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 160) String displayName,
        @Size(max = 240) String locationText,
        Boolean clearLocationText,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public WarehouseUpdateRequest {
        code = code == null ? null : Warehouse.canonicalCode(code);
        displayName = displayName == null ? null : Warehouse.canonicalDisplayName(displayName);
        locationText = locationText == null ? null : Warehouse.canonicalLocation(locationText);
        clearLocationText = Boolean.TRUE.equals(clearLocationText);
        reasonCode = WarehouseCreateRequest.normalizeReason(reasonCode);
        if (locationText != null && clearLocationText) {
            throw new IllegalArgumentException("locationText cannot be set and cleared together");
        }
        if (code == null && displayName == null && locationText == null && !clearLocationText) {
            throw new IllegalArgumentException("at least one warehouse field must be provided");
        }
    }
}
