package com.agriinsight.backend.cost.api;

import com.agriinsight.backend.cost.application.OperatingCostRecord;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OperatingCostResponse(
        UUID id,
        CostTarget.Type targetType,
        UUID targetId,
        CostCategory category,
        BigDecimal amountVnd,
        BigDecimal signedAmountVnd,
        CostEntryKind entryKind,
        Instant occurredAt,
        String description,
        String sourceReference,
        UUID reversalOf,
        long version) {

    static OperatingCostResponse from(OperatingCostRecord entry) {
        return new OperatingCostResponse(
                entry.id(), entry.target().type(), entry.target().id().orElse(null),
                entry.category(), entry.amountVnd(), entry.signedAmountVnd(), entry.kind(),
                entry.occurredAt(), entry.description().orElse(null),
                entry.sourceReference().orElse(null), entry.reversalOf().orElse(null),
                entry.version());
    }
}
