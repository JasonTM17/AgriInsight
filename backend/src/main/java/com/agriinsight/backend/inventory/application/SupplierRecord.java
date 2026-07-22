package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.Supplier;
import java.util.Objects;
import java.util.UUID;

public record SupplierRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        boolean active,
        long version) {

    public SupplierRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Supplier.canonicalCode(code);
        displayName = Supplier.canonicalDisplayName(displayName);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
