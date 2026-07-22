package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostEntryKind;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record OperatingCostRecord(
        UUID id,
        UUID tenantId,
        CostTarget target,
        CostCategory category,
        BigDecimal amountVnd,
        CostEntryKind kind,
        Instant occurredAt,
        Optional<String> description,
        Optional<String> sourceReference,
        Optional<UUID> reversalOf,
        UUID commandReference,
        UUID recordedByProfileId,
        long version) {

    public OperatingCostRecord {
        new OperatingCostEntry(
                id, tenantId, target, category, amountVnd, kind, occurredAt,
                description, sourceReference, reversalOf, commandReference,
                recordedByProfileId);
        if (version != 0) {
            throw new IllegalArgumentException("Append-only cost entries must remain at version zero");
        }
    }

    public BigDecimal signedAmountVnd() {
        return kind == CostEntryKind.REVERSAL ? amountVnd.negate() : amountVnd;
    }
}
