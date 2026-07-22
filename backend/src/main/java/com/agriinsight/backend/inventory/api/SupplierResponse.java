package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.SupplierRecord;
import java.util.Objects;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String code,
        String displayName,
        boolean active,
        long version) {

    public SupplierResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    public static SupplierResponse from(SupplierRecord supplier) {
        Objects.requireNonNull(supplier, "supplier is required");
        return new SupplierResponse(
                supplier.id(),
                supplier.code(),
                supplier.displayName(),
                supplier.active(),
                supplier.version());
    }
}
