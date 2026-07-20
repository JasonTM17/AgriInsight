package com.agriinsight.backend.shared.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface IdempotencyConflictPublisher {

    void publish(Conflict conflict);

    record Conflict(
            UUID tenantId,
            UUID principalId,
            UUID commandId,
            String routeTemplate,
            Optional<String> correlationId) {

        public Conflict {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(principalId, "principalId is required");
            Objects.requireNonNull(commandId, "commandId is required");
            Objects.requireNonNull(routeTemplate, "routeTemplate is required");
            correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
        }
    }
}
