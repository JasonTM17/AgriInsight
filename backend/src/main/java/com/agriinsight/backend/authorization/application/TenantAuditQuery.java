package com.agriinsight.backend.authorization.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record TenantAuditQuery(
        int limit,
        int offset,
        Optional<UUID> actorProfileId,
        Optional<String> action,
        Optional<String> targetType,
        Optional<UUID> targetId,
        Optional<TenantAuditEvent.Outcome> outcome) {

    private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private static final Pattern TARGET_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public TenantAuditQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
        actorProfileId = Objects.requireNonNull(actorProfileId, "actorProfileId is required");
        action = normalized(action, ACTION, "action");
        targetType = normalized(targetType, TARGET_TYPE, "targetType");
        targetId = Objects.requireNonNull(targetId, "targetId is required");
        outcome = Objects.requireNonNull(outcome, "outcome is required");
    }

    private static Optional<String> normalized(
            Optional<String> value, Pattern pattern, String fieldName) {
        Optional<String> required = Objects.requireNonNull(value, fieldName + " is required")
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .map(item -> item.toUpperCase(Locale.ROOT));
        required.ifPresent(item -> {
            if (!pattern.matcher(item).matches()) {
                throw new IllegalArgumentException(fieldName + " has an invalid format");
            }
        });
        return required;
    }
}
