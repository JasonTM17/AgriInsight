package com.agriinsight.backend.operations.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityLogAccess(
        UUID farmId,
        boolean manager,
        Optional<UUID> workerEmployeeId) {

    public ActivityLogAccess {
        Objects.requireNonNull(farmId, "farmId is required");
        workerEmployeeId = Objects.requireNonNull(workerEmployeeId, "workerEmployeeId is required");
        if (!manager && workerEmployeeId.isEmpty()) {
            throw new IllegalArgumentException("activity log access requires manager or worker scope");
        }
    }
}
