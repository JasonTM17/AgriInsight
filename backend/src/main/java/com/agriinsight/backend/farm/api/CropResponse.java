package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.CropRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CropResponse(
        UUID id,
        String code,
        String displayName,
        Optional<String> scientificName,
        boolean active,
        long version) {

    public CropResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(scientificName, "scientificName is required");
    }

    public static CropResponse from(CropRecord crop) {
        Objects.requireNonNull(crop, "crop is required");
        return new CropResponse(
                crop.id(), crop.code(), crop.displayName(), crop.scientificName(),
                crop.active(), crop.version());
    }
}
