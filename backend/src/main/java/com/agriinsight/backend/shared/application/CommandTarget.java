package com.agriinsight.backend.shared.application;

import java.util.Objects;
import java.util.UUID;

public record CommandTarget(
        String resourceType,
        UUID resourceId,
        long resourceVersion) {

    public CommandTarget {
        String requiredType = Objects.requireNonNull(resourceType, "resourceType is required");
        if (!requiredType.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("resourceType has an invalid format");
        }
        Objects.requireNonNull(resourceId, "resourceId is required");
        if (resourceVersion < 0) {
            throw new IllegalArgumentException("resourceVersion must not be negative");
        }
    }
}
