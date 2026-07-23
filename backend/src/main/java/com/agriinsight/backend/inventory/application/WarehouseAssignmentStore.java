package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.WarehouseAssignment;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseAssignmentStore {

    WarehouseAssignmentPage findAll(
            ScopeContext scope, WarehouseAssignmentQuery query);

    Optional<WarehouseAssignmentRecord> findById(
            ScopeContext scope, UUID assignmentId);

    Optional<WarehouseAssignmentRecord> findActive(
            ScopeContext scope,
            UUID userProfileId,
            UUID warehouseId);

    boolean activeProfileExists(ScopeContext scope, UUID userProfileId);

    boolean activeWarehouseExists(ScopeContext scope, UUID warehouseId);

    Optional<WarehouseAssignmentRecord> create(
            ScopeContext scope, WarehouseAssignment assignment);

    Optional<WarehouseAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion);
}
