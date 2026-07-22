package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public record ActivityUpdateRequest(
        ActivityType activityType,
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String title,
        @Size(max = Activity.DESCRIPTION_MAX_LENGTH) String description,
        Boolean clearDescription,
        Instant plannedStartAt,
        Instant dueAt,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityUpdateRequest {
        code = code == null ? null : Activity.canonicalCode(code);
        title = title == null ? null : Activity.canonicalTitle(title);
        description = normalizeDescription(description);
        clearDescription = Boolean.TRUE.equals(clearDescription);
        reasonCode = normalizeReason(reasonCode);
        if (clearDescription && description != null) {
            throw new IllegalArgumentException("description cannot be set and cleared together");
        }
        if (plannedStartAt != null && dueAt != null) {
            Activity.requireSchedule(plannedStartAt, dueAt);
        }
        if (activityType == null && code == null && title == null && description == null
                && !clearDescription && plannedStartAt == null && dueAt == null) {
            throw new IllegalArgumentException("at least one activity field must be provided");
        }
    }

    private static String normalizeDescription(String value) {
        return value == null ? null
                : Activity.optionalDescription(Optional.of(value)).orElseThrow();
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
