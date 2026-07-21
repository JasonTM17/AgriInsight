package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Farm;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record FarmUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String displayName,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FarmUpdateRequest {
        code = code == null ? null : Farm.canonicalCode(code);
        displayName = displayName == null ? null : Farm.canonicalDisplayName(displayName);
        reasonCode = normalizeReason(reasonCode);
        if (code == null && displayName == null) {
            throw new IllegalArgumentException("at least one farm field must be provided");
        }
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
