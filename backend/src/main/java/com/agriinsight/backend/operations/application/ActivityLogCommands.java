package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ActivityLogCommands {

    private ActivityLogCommands() {
    }

    public record Append(
            UUID employeeId,
            Instant occurredAt,
            Optional<String> notes,
            Optional<BigDecimal> quantity,
            Optional<ActivityLogUnit> unit,
            Optional<String> evidenceUri,
            TenantAuditMetadata audit) {

        public Append {
            Objects.requireNonNull(employeeId, "employeeId is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            notes = ActivityLog.optionalText(notes, "notes", ActivityLog.NOTES_MAX_LENGTH);
            quantity = ActivityLog.optionalQuantity(quantity);
            unit = Objects.requireNonNull(unit, "unit is required");
            evidenceUri = ActivityLog.optionalEvidenceUri(evidenceUri);
            ActivityLog.validatePayload(
                    notes, quantity, unit, evidenceUri,
                    false, Optional.empty(), Optional.empty());
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Correct(
            ActivityLogCorrectionKind correctionKind,
            Instant occurredAt,
            Optional<String> notes,
            Optional<BigDecimal> quantity,
            Optional<ActivityLogUnit> unit,
            Optional<String> evidenceUri,
            String correctionReason,
            TenantAuditMetadata audit) {

        public Correct {
            Objects.requireNonNull(correctionKind, "correctionKind is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            notes = ActivityLog.optionalText(notes, "notes", ActivityLog.NOTES_MAX_LENGTH);
            quantity = ActivityLog.optionalQuantity(quantity);
            unit = Objects.requireNonNull(unit, "unit is required");
            evidenceUri = ActivityLog.optionalEvidenceUri(evidenceUri);
            correctionReason = ActivityLog.optionalText(
                    Optional.ofNullable(correctionReason),
                    "correctionReason",
                    ActivityLog.CORRECTION_REASON_MAX_LENGTH).orElseThrow();
            ActivityLog.validatePayload(
                    notes, quantity, unit, evidenceUri,
                    true, Optional.of(correctionKind),
                    Optional.of(correctionReason));
            Objects.requireNonNull(audit, "audit is required");
        }
    }

}
