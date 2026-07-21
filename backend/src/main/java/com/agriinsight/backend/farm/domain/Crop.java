package com.agriinsight.backend.farm.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Crop(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        Optional<String> scientificName) {

    public Crop {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = canonicalCode(code);
        displayName = canonicalDisplayName(displayName);
        scientificName = optionalScientificName(scientificName);
    }

    public static String canonicalCode(String value) {
        return Farm.canonicalCode(value);
    }

    public static String canonicalDisplayName(String value) {
        return Farm.canonicalDisplayName(value);
    }

    public static Optional<String> optionalScientificName(Optional<String> value) {
        return Objects.requireNonNull(value, "scientificName is required")
                .map(Crop::canonicalScientificName);
    }

    public static String canonicalScientificName(String value) {
        String normalized = Objects.requireNonNull(value, "scientificName is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("scientificName must not be blank");
        }
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("scientificName must not exceed 200 characters");
        }
        return normalized;
    }
}
