package com.agriinsight.backend.inventory.api;

import com.agriinsight.backend.inventory.application.WarehouseAssignmentRecord;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WarehouseAssignmentResponse(
        UUID id,
        UUID userProfileId,
        UUID warehouseId,
        Optional<Instant> revokedAt,
        boolean active,
        long version) {

    public WarehouseAssignmentResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(userProfileId, "userProfileId is required");
        Objects.requireNonNull(warehouseId, "warehouseId is required");
        revokedAt = Objects.requireNonNull(revokedAt, "revokedAt is required");
    }

    public static WarehouseAssignmentResponse from(WarehouseAssignmentRecord assignment) {
        Objects.requireNonNull(assignment, "assignment is required");
        return new WarehouseAssignmentResponse(
                assignment.id(),
                assignment.userProfileId(),
                assignment.warehouseId(),
                assignment.revokedAt(),
                assignment.active(),
                assignment.version());
    }
}
