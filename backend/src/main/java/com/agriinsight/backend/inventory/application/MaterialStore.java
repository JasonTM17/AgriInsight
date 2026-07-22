package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.Material;
import java.util.Optional;
import java.util.UUID;

public interface MaterialStore {

    MaterialPage findAll(ScopeContext scope, MaterialQuery query);

    Optional<MaterialRecord> findById(ScopeContext scope, UUID materialId);

    MaterialRecord create(ScopeContext scope, Material material);

    Optional<MaterialRecord> update(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            MaterialCommands.Update command);

    Optional<MaterialRecord> updateActive(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            boolean active);

    boolean hasReferences(ScopeContext scope, UUID materialId);
}
