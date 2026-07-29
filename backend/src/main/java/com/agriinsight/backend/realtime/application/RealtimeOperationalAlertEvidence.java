package com.agriinsight.backend.realtime.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Safe evidence reference exposed by the operational-alert read boundary. */
public record RealtimeOperationalAlertEvidence(Type type, Optional<UUID> id) {

    public RealtimeOperationalAlertEvidence {
        type = Objects.requireNonNull(type, "type is required");
        id = Objects.requireNonNull(id, "id is required");
        if ((type == Type.TENANT_BACKLOG) == id.isPresent()) {
            throw new IllegalArgumentException("Evidence type and identifier do not match");
        }
    }

    public static RealtimeOperationalAlertEvidence from(
            RealtimeOperationalAlertPolicy policy,
            UUID sourceEventId) {
        return switch (Objects.requireNonNull(policy, "policy is required")) {
            case OUTBOX_PUBLISH_BACKLOG -> {
                if (sourceEventId != null) {
                    throw new IllegalArgumentException("Tenant backlog evidence must not have an identifier");
                }
                yield new RealtimeOperationalAlertEvidence(Type.TENANT_BACKLOG, Optional.empty());
            }
            case REALTIME_DELIVERY_LAG, REALTIME_DLT_RECORD -> {
                if (sourceEventId == null) {
                    throw new IllegalArgumentException("Operational event evidence requires an identifier");
                }
                yield new RealtimeOperationalAlertEvidence(
                        Type.OPERATIONAL_EVENT, Optional.of(sourceEventId));
            }
        };
    }

    public enum Type {
        TENANT_BACKLOG,
        OPERATIONAL_EVENT
    }
}
