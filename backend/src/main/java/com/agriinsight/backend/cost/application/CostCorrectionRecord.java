package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.cost.domain.CostEntryKind;
import java.util.Objects;

public record CostCorrectionRecord(
        OperatingCostRecord reversal,
        OperatingCostRecord replacement) {

    public CostCorrectionRecord {
        Objects.requireNonNull(reversal, "reversal is required");
        Objects.requireNonNull(replacement, "replacement is required");
        if (reversal.kind() != CostEntryKind.REVERSAL
                || replacement.kind() != CostEntryKind.POSTING
                || !reversal.commandReference().equals(replacement.commandReference())) {
            throw new IllegalArgumentException(
                    "A correction requires one reversal and one replacement from the same command");
        }
    }
}
