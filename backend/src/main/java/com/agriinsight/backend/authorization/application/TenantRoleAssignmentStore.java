package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleAssignmentStore {

    boolean profileExists(ScopeContext scope, UUID profileId);

    TenantRoleAssignmentPage findAll(
            ScopeContext scope,
            UUID profileId,
            TenantRoleAssignmentQuery query);

    Optional<TenantRoleAssignment> find(
            ScopeContext scope,
            UUID profileId,
            Role role);

    Optional<TenantRoleAssignment> create(
            ScopeContext scope,
            UUID assignmentId,
            UUID profileId,
            Role role);

    Optional<TenantRoleAssignment> reactivate(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion);

    Optional<TenantRoleAssignment> revoke(
            ScopeContext scope,
            UUID profileId,
            Role role,
            long expectedVersion);
}
