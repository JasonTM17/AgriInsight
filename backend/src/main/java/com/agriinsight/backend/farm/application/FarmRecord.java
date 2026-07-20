package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.farm.domain.Farm;
import java.util.Objects;
import java.util.UUID;

public record FarmRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        boolean active,
        long version) {

    public FarmRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Farm.canonicalCode(code);
        displayName = Farm.canonicalDisplayName(displayName);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
