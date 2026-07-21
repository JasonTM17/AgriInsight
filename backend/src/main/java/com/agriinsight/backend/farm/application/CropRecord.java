package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.farm.domain.Crop;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CropRecord(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        Optional<String> scientificName,
        boolean active,
        long version) {

    public CropRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = Crop.canonicalCode(code);
        displayName = Crop.canonicalDisplayName(displayName);
        scientificName = Crop.optionalScientificName(scientificName);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
