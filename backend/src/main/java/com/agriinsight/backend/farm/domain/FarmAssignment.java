package com.agriinsight.backend.farm.domain;

import java.util.Objects;
import java.util.UUID;

public record FarmAssignment(
        UUID id,
        UUID tenantId,
        UUID userProfileId,
        UUID farmId) {

    public FarmAssignment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(farmId, "farmId is required");
    }
}
