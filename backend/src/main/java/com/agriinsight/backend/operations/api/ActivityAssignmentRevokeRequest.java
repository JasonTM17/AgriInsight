package com.agriinsight.backend.operations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record ActivityAssignmentRevokeRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityAssignmentRevokeRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
