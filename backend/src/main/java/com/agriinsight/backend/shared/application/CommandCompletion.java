package com.agriinsight.backend.shared.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CommandCompletion<T>(
        int responseStatus,
        CommandTarget target,
        Optional<T> representation) {

    public CommandCompletion {
        if (responseStatus < 200 || responseStatus > 299) {
            throw new IllegalArgumentException("responseStatus must be successful");
        }
        Objects.requireNonNull(target, "target is required");
        representation = Objects.requireNonNull(representation, "representation is required");
    }

    public static <T> CommandCompletion<T> withRepresentation(
            int responseStatus,
            String resourceType,
            UUID resourceId,
            long resourceVersion,
            T representation) {
        return new CommandCompletion<>(
                responseStatus,
                new CommandTarget(resourceType, resourceId, resourceVersion),
                Optional.of(Objects.requireNonNull(representation, "representation is required")));
    }

    public static <T> CommandCompletion<T> withoutRepresentation(
            int responseStatus,
            String resourceType,
            UUID resourceId,
            long resourceVersion) {
        return new CommandCompletion<>(
                responseStatus,
                new CommandTarget(resourceType, resourceId, resourceVersion),
                Optional.empty());
    }
}
