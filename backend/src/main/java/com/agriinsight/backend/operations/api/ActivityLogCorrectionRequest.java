package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ActivityLogCorrectionRequest(
        @NotNull ActivityLogCorrectionKind correctionKind,
        @NotNull Instant occurredAt,
        @Size(max = ActivityLog.NOTES_MAX_LENGTH) String notes,
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 14, fraction = 4) BigDecimal quantity,
        ActivityLogUnit unit,
        @Size(max = ActivityLog.EVIDENCE_URI_MAX_LENGTH)
        @Pattern(regexp = "^(https|s3|gs|az)://[^\\s]+$") String evidenceUri,
        @NotBlank @Size(max = ActivityLog.CORRECTION_REASON_MAX_LENGTH) String correctionReason,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public ActivityLogCorrectionRequest {
        notes = ActivityLogAppendRequest.normalizeOptional(notes);
        evidenceUri = ActivityLogAppendRequest.normalizeOptional(evidenceUri);
        correctionReason = correctionReason == null ? null : correctionReason.strip();
        reasonCode = ActivityLogAppendRequest.normalizeReason(reasonCode);
    }
}
