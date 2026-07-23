package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantAuditRecord;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TenantAuditResponse(
        UUID id,
        String actorType,
        Optional<UUID> actorProfileId,
        String action,
        String targetType,
        Optional<UUID> targetId,
        Optional<String> reasonCode,
        Optional<String> correlationId,
        String outcome,
        Instant occurredAt) {

    public TenantAuditResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(actorType, "actorType is required");
        actorProfileId = Objects.requireNonNull(actorProfileId, "actorProfileId is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(targetType, "targetType is required");
        targetId = Objects.requireNonNull(targetId, "targetId is required");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode is required");
        correlationId = Objects.requireNonNull(correlationId, "correlationId is required");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public static TenantAuditResponse from(TenantAuditRecord event) {
        Objects.requireNonNull(event, "event is required");
        return new TenantAuditResponse(
                event.id(),
                event.actorType().name(),
                event.actorProfileId(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.reasonCode(),
                event.correlationId(),
                event.outcome().name(),
                event.occurredAt());
    }
}
