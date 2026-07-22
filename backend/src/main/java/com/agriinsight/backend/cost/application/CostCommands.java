package com.agriinsight.backend.cost.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.cost.domain.OperatingCostEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class CostCommands {

    public static final int CORRECTION_REASON_MAX_LENGTH = 500;

    private CostCommands() {
    }

    public record Post(
            CostTarget target,
            CostCategory category,
            BigDecimal amountVnd,
            Instant occurredAt,
            Optional<String> description,
            Optional<String> sourceReference,
            TenantAuditMetadata audit) {

        public Post {
            Objects.requireNonNull(target, "target is required");
            Objects.requireNonNull(category, "category is required");
            amountVnd = OperatingCostEntry.positiveVnd(amountVnd);
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            description = OperatingCostEntry.optionalText(
                    description, "description", OperatingCostEntry.DESCRIPTION_MAX_LENGTH);
            sourceReference = OperatingCostEntry.optionalText(
                    sourceReference,
                    "sourceReference",
                    OperatingCostEntry.SOURCE_REFERENCE_MAX_LENGTH);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Correct(
            CostTarget target,
            CostCategory category,
            BigDecimal amountVnd,
            Instant occurredAt,
            Optional<String> description,
            Optional<String> sourceReference,
            String correctionReason,
            TenantAuditMetadata audit) {

        public Correct {
            Post validated = new Post(
                    target, category, amountVnd, occurredAt,
                    description, sourceReference, audit);
            target = validated.target();
            category = validated.category();
            amountVnd = validated.amountVnd();
            occurredAt = validated.occurredAt();
            description = validated.description();
            sourceReference = validated.sourceReference();
            Objects.requireNonNull(correctionReason, "correctionReason is required");
            correctionReason = OperatingCostEntry.optionalText(
                            Optional.of(correctionReason),
                            "correctionReason",
                            CORRECTION_REASON_MAX_LENGTH)
                    .orElseThrow();
            audit = validated.audit();
        }
    }
}
