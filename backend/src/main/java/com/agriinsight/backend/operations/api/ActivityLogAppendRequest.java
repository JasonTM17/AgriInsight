package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record ActivityLogAppendRequest(
        @NotNull UUID employeeId,
        @NotNull Instant occurredAt,
        @Size(max = ActivityLog.NOTES_MAX_LENGTH) String notes,
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 14, fraction = 4) BigDecimal quantity,
        ActivityLogUnit unit,
        @Size(max = ActivityLog.EVIDENCE_URI_MAX_LENGTH)
        @Pattern(regexp = "^(https|s3|gs|az)://[^\\s]+$") String evidenceUri,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityLogAppendRequest {
        notes = normalizeOptional(notes);
        evidenceUri = normalizeOptional(evidenceUri);
        reasonCode = normalizeReason(reasonCode);
    }

    static String normalizeOptional(String value) {
        return value == null ? null : value.strip();
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank()
                ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
