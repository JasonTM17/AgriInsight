package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Harvest;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface HarvestStore {
    HarvestPage findAll(ScopeContext scope, HarvestQuery query);
    Optional<HarvestRecord> findById(ScopeContext scope, UUID harvestId);
    boolean farmVisible(ScopeContext scope, UUID farmId);
    boolean postTargetAvailable(
            ScopeContext scope, UUID farmId, UUID fieldId,
            UUID seasonId, UUID cropId, LocalDate occurredOn);
    Optional<HarvestRecord> append(ScopeContext scope, Harvest harvest);
}
