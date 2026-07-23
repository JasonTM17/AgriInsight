package com.agriinsight.backend.authorization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record TenantAuditRecord(
        UUID id,
        ActorType actorType,
        Optional<UUID> actorProfileId,
        String action,
        String targetType,
        Optional<UUID> targetId,
        Optional<String> reasonCode,
        Optional<String> correlationId,
        TenantAuditEvent.Outcome outcome,
        Instant occurredAt) {

    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern TARGET_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public TenantAuditRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(actorType, "actorType is required");
        actorProfileId = Objects.requireNonNull(actorProfileId, "actorProfileId is required");
        action = requiredConstrained(action, REASON_CODE, "action");
        targetType = requiredConstrained(targetType, TARGET_TYPE, "targetType");
        targetId = Objects.requireNonNull(targetId, "targetId is required");
        reasonCode = constrained(reasonCode, REASON_CODE, "reasonCode");
        correlationId = constrained(correlationId, CORRELATION_ID, "correlationId");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (actorType == ActorType.TENANT_USER && actorProfileId.isEmpty()) {
            throw new IllegalArgumentException("tenant user audit actor requires actorProfileId");
        }
        if (actorType == ActorType.OPERATOR && actorProfileId.isPresent()) {
            throw new IllegalArgumentException("operator audit actor must not expose actorProfileId");
        }
    }

    private static Optional<String> constrained(
            Optional<String> value, Pattern pattern, String fieldName) {
        Optional<String> required = Objects.requireNonNull(value, fieldName + " is required");
        required.ifPresent(item -> {
            if (!pattern.matcher(item).matches()) {
                throw new IllegalArgumentException(fieldName + " has an invalid format");
            }
        });
        return required;
    }

    private static String requiredConstrained(
            String value, Pattern pattern, String fieldName) {
        String required = Objects.requireNonNull(value, fieldName + " is required");
        if (!pattern.matcher(required).matches()) {
            throw new IllegalArgumentException(fieldName + " has an invalid format");
        }
        return required;
    }

    public enum ActorType {
        TENANT_USER,
        OPERATOR
    }
}
