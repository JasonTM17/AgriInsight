package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record ActivityCreateRequest(
        @NotNull UUID farmId,
        @NotNull UUID fieldId,
        @NotNull UUID seasonId,
        @NotNull ActivityType activityType,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 200) String title,
        @Size(max = Activity.DESCRIPTION_MAX_LENGTH) String description,
        @NotNull Instant plannedStartAt,
        @NotNull Instant dueAt,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityCreateRequest {
        code = code == null ? null : Activity.canonicalCode(code);
        title = title == null ? null : Activity.canonicalTitle(title);
        description = normalizeDescription(description);
        if (plannedStartAt != null && dueAt != null) {
            Activity.requireSchedule(plannedStartAt, dueAt);
        }
        reasonCode = normalizeReason(reasonCode);
    }

    private static String normalizeDescription(String value) {
        return value == null ? null
                : Activity.optionalDescription(Optional.of(value)).orElseThrow();
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
