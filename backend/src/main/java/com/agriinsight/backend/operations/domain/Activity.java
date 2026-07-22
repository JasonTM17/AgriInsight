package com.agriinsight.backend.operations.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record Activity(
        UUID id,
        UUID tenantId,
        UUID farmId,
        UUID fieldId,
        UUID seasonId,
        ActivityType activityType,
        String code,
        String title,
        Optional<String> description,
        Instant plannedStartAt,
        Instant dueAt) {

    public static final int DESCRIPTION_MAX_LENGTH = 2_000;

    private static final Pattern CODE_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    public Activity {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        Objects.requireNonNull(activityType, "activityType is required");
        code = canonicalCode(code);
        title = canonicalTitle(title);
        description = optionalDescription(description);
        requireSchedule(plannedStartAt, dueAt);
    }

    public static String canonicalCode(String value) {
        String normalized = requiredText(value, "code", 64).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code has an invalid format");
        }
        return normalized;
    }

    public static String canonicalTitle(String value) {
        return requiredText(value, "title", 200);
    }

    public static Optional<String> optionalDescription(Optional<String> value) {
        return Objects.requireNonNull(value, "description is required")
                .map(description -> requiredText(description, "description", DESCRIPTION_MAX_LENGTH));
    }

    public static void requireSchedule(Instant plannedStartAt, Instant dueAt) {
        Instant requiredStart = Objects.requireNonNull(plannedStartAt, "plannedStartAt is required");
        Instant requiredDue = Objects.requireNonNull(dueAt, "dueAt is required");
        if (requiredDue.isBefore(requiredStart)) {
            throw new IllegalArgumentException("dueAt must not be before plannedStartAt");
        }
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
