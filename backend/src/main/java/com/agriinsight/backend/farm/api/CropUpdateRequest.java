package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Crop;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CropUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String displayName,
        @Size(max = 200) String scientificName,
        Boolean clearScientificName,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public CropUpdateRequest {
        code = code == null ? null : Crop.canonicalCode(code);
        displayName = displayName == null ? null : Crop.canonicalDisplayName(displayName);
        scientificName = scientificName == null ? null : Crop.canonicalScientificName(scientificName);
        clearScientificName = Boolean.TRUE.equals(clearScientificName);
        reasonCode = CropCreateRequest.normalizeReason(reasonCode);
        if (scientificName != null && clearScientificName) {
            throw new IllegalArgumentException("scientificName cannot be set and cleared together");
        }
        if (code == null && displayName == null && scientificName == null && !clearScientificName) {
            throw new IllegalArgumentException("at least one crop value must be provided");
        }
    }
}
