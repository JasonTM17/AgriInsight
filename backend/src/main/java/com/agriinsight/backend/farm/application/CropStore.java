package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Crop;
import java.util.Optional;
import java.util.UUID;

public interface CropStore {

    CropPage findAll(ScopeContext scope, CropQuery query);

    Optional<CropRecord> findById(ScopeContext scope, UUID cropId);

    CropRecord create(ScopeContext scope, Crop crop);

    Optional<CropRecord> update(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            CropCommands.Update command);

    Optional<CropRecord> updateActive(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            boolean active);

    boolean hasDeactivationBlockers(ScopeContext scope, UUID cropId);
}
