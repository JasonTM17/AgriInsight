package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;

public interface OperatingCostQueryStore {

    OperatingCostPage findAll(ScopeContext scope, OperatingCostQuery query);

    Optional<OperatingCostRecord> findById(ScopeContext scope, UUID entryId);

    CostSummaryResult summarize(ScopeContext scope, CostSummaryQuery query);
}
