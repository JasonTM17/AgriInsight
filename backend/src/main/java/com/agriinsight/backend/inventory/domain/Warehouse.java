package com.agriinsight.backend.inventory.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record Warehouse(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        Optional<String> locationText) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    public Warehouse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = canonicalCode(code);
        displayName = requiredText(displayName, "displayName", 160);
        locationText = Objects.requireNonNull(locationText, "locationText is required")
                .map(Warehouse::canonicalLocation);
    }

    public static String canonicalCode(String value) {
        String normalized = requiredText(value, "code", 64).toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code has an invalid format");
        }
        return normalized;
    }

    public static String canonicalDisplayName(String value) {
        return requiredText(value, "displayName", 160);
    }

    public static String canonicalLocation(String value) {
        return requiredText(value, "locationText", 240);
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
