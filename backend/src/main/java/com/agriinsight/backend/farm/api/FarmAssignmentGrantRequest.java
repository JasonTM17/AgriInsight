package com.agriinsight.backend.farm.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.UUID;

public record FarmAssignmentGrantRequest(
        @NotNull UUID userProfileId,
        @NotNull UUID farmId,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FarmAssignmentGrantRequest {
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? null
                : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
