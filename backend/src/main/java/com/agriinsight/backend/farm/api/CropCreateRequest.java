package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Crop;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CropCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 200) String scientificName,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public CropCreateRequest {
        code = code == null ? null : Crop.canonicalCode(code);
        displayName = displayName == null ? null : Crop.canonicalDisplayName(displayName);
        scientificName = scientificName == null ? null : Crop.canonicalScientificName(scientificName);
        reasonCode = normalizeReason(reasonCode);
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
