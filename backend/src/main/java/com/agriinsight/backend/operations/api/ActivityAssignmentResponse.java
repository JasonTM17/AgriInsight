package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityAssignmentRecord;
import java.util.Objects;
import java.util.UUID;

public record ActivityAssignmentResponse(
        UUID id,
        UUID activityId,
        UUID employeeId,
        boolean active,
        long version) {

    public ActivityAssignmentResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(employeeId, "employeeId is required");
    }

    public static ActivityAssignmentResponse from(ActivityAssignmentRecord assignment) {
        Objects.requireNonNull(assignment, "assignment is required");
        return new ActivityAssignmentResponse(
                assignment.id(), assignment.activityId(), assignment.employeeId(),
                assignment.active(), assignment.version());
    }
}
