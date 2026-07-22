package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.Supplier;
import java.util.Optional;
import java.util.UUID;

public interface SupplierStore {

    SupplierPage findAll(ScopeContext scope, SupplierQuery query);

    Optional<SupplierRecord> findById(ScopeContext scope, UUID supplierId);

    SupplierRecord create(ScopeContext scope, Supplier supplier);

    Optional<SupplierRecord> update(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            SupplierCommands.Update command);

    Optional<SupplierRecord> updateActive(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            boolean active);

    boolean hasReferences(ScopeContext scope, UUID supplierId);
}
