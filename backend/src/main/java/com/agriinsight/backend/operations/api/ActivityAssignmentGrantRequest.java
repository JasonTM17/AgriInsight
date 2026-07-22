package com.agriinsight.backend.operations.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.UUID;

public record ActivityAssignmentGrantRequest(
        @NotNull UUID employeeId,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityAssignmentGrantRequest {
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
