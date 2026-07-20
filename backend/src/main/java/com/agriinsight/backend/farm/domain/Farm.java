package com.agriinsight.backend.farm.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Farm(
        UUID id,
        UUID tenantId,
        String code,
        String displayName) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    public Farm {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = canonicalCode(code);
        displayName = canonicalDisplayName(displayName);
    }

    public static String canonicalCode(String value) {
        String normalized = requiredText(value, "code", 64).toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code has an invalid format");
        }
        return normalized;
    }

    public static String canonicalDisplayName(String value) {
        return requiredText(value, "displayName", 200);
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
