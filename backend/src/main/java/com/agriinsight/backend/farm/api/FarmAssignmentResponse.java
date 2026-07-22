package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FarmAssignmentRecord;
import java.util.Objects;
import java.util.UUID;

public record FarmAssignmentResponse(
        UUID id,
        UUID userProfileId,
        UUID farmId,
        boolean active,
        long version) {

    public static FarmAssignmentResponse from(FarmAssignmentRecord assignment) {
        Objects.requireNonNull(assignment, "assignment is required");
        return new FarmAssignmentResponse(
                assignment.id(),
                assignment.userProfileId(),
                assignment.farmId(),
                assignment.active(),
                assignment.version());
    }
}
