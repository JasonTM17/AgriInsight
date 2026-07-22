package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.ActivityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Locale;

public record ActivityTransitionRequest(
        @NotNull ActivityStatus targetStatus,
        @NotNull Instant effectiveAt,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityTransitionRequest {
        reasonCode = reasonCode == null ? null : reasonCode.strip().toUpperCase(Locale.ROOT);
    }
}
