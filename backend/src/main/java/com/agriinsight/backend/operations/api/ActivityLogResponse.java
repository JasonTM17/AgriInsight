package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.ActivityLogRecord;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ActivityLogResponse(
        UUID id,
        UUID activityId,
        UUID employeeId,
        UUID authorProfileId,
        Instant occurredAt,
        Optional<String> notes,
        Optional<BigDecimal> quantity,
        Optional<ActivityLogUnit> unit,
        Optional<String> evidenceUri,
        Optional<UUID> correctsLogId,
        Optional<ActivityLogCorrectionKind> correctionKind,
        Optional<String> correctionReason,
        long version) {

    public ActivityLogResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(employeeId, "employeeId is required");
        Objects.requireNonNull(authorProfileId, "authorProfileId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(notes, "notes is required");
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(unit, "unit is required");
        Objects.requireNonNull(evidenceUri, "evidenceUri is required");
        Objects.requireNonNull(correctsLogId, "correctsLogId is required");
        Objects.requireNonNull(correctionKind, "correctionKind is required");
        Objects.requireNonNull(correctionReason, "correctionReason is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public static ActivityLogResponse from(ActivityLogRecord log) {
        Objects.requireNonNull(log, "log is required");
        return new ActivityLogResponse(
                log.id(), log.activityId(), log.employeeId(), log.authorProfileId(),
                log.occurredAt(), log.notes(), log.quantity(), log.unit(), log.evidenceUri(),
                log.correctsLogId(), log.correctionKind(), log.correctionReason(), log.version());
    }
}
