package com.agriinsight.backend.operations.domain;

import java.util.Objects;
import java.util.UUID;

public record ActivityAssignment(
        UUID id,
        UUID tenantId,
        UUID activityId,
        UUID employeeId) {

    public ActivityAssignment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(employeeId, "employeeId is required");
    }
}
