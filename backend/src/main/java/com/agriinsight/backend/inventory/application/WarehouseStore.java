package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.Warehouse;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseStore {

    WarehousePage findAll(ScopeContext scope, WarehouseQuery query);

    Optional<WarehouseRecord> findById(ScopeContext scope, UUID warehouseId);

    WarehouseRecord create(ScopeContext scope, Warehouse warehouse);

    Optional<WarehouseRecord> update(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> locationText);

    Optional<WarehouseRecord> updateActive(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            boolean active);

    boolean hasDeactivationBlockers(ScopeContext scope, UUID warehouseId);
}
