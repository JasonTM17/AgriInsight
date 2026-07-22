package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.domain.Supplier;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 160) String displayName,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public SupplierUpdateRequest {
        code = code == null ? null : Supplier.canonicalCode(code);
        displayName = displayName == null ? null : Supplier.canonicalDisplayName(displayName);
        reasonCode = SupplierCreateRequest.normalizeReason(reasonCode);
        if (code == null && displayName == null) {
            throw new IllegalArgumentException("at least one supplier field must be provided");
        }
    }
}
