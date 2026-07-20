package com.agriinsight.backend.authorization.api;

import com.agriinsight.backend.authorization.application.TenantRoleAssignment;
import java.util.Objects;
import java.util.UUID;

public record TenantRoleResponse(
        UUID id,
        UUID profileId,
        String roleCode,
        boolean active,
        long version) {

    public TenantRoleResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(profileId, "profileId is required");
        Objects.requireNonNull(roleCode, "roleCode is required");
    }

    public static TenantRoleResponse from(TenantRoleAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment is required");
        return new TenantRoleResponse(
                assignment.id(),
                assignment.profileId(),
                assignment.role().name(),
                assignment.active(),
                assignment.version());
    }
}
