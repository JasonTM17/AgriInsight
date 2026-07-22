package com.agriinsight.backend.cost.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CostTarget(Type type, Optional<UUID> id) {

    public CostTarget {
        Objects.requireNonNull(type, "type is required");
        id = Objects.requireNonNull(id, "id is required");
        if ((type == Type.TENANT) != id.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tenant targets omit an id; domain targets require exactly one id");
        }
    }

    public static CostTarget tenant() {
        return new CostTarget(Type.TENANT, Optional.empty());
    }

    public static CostTarget domain(Type type, UUID id) {
        if (Objects.requireNonNull(type, "type is required") == Type.TENANT) {
            throw new IllegalArgumentException("Use tenant() for a tenant target");
        }
        return new CostTarget(type, Optional.of(Objects.requireNonNull(id, "id is required")));
    }

    public enum Type {
        TENANT,
        FARM,
        FIELD,
        SEASON,
        ACTIVITY
    }
}
