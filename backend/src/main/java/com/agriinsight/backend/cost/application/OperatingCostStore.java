package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import java.util.Optional;
import java.util.UUID;

public interface OperatingCostStore {

    boolean targetAvailable(ScopeContext scope, CostTarget target);

    Optional<OperatingCostRecord> findById(ScopeContext scope, UUID entryId);

    Optional<OperatingCostRecord> append(
            ScopeContext scope, OperatingCostEntry entry);

    Optional<CostCorrectionRecord> appendCorrection(
            ScopeContext scope,
            UUID originalEntryId,
            OperatingCostEntry reversal,
            OperatingCostEntry replacement);

    Optional<CostCorrectionRecord> findCorrectionByReplacementId(
            ScopeContext scope, UUID replacementEntryId);
}
