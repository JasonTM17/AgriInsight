package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ActivityLogRecord(
        UUID id,
        UUID tenantId,
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

    public ActivityLogRecord {
        new ActivityLog(
                id, tenantId, activityId, employeeId, authorProfileId, occurredAt,
                notes, quantity, unit, evidenceUri, correctsLogId, correctionKind, correctionReason);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
