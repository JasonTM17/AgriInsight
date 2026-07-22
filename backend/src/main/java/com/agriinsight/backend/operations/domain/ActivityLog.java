package com.agriinsight.backend.operations.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record ActivityLog(
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
        Optional<String> correctionReason) {

    public static final int NOTES_MAX_LENGTH = 2_000;
    public static final int EVIDENCE_URI_MAX_LENGTH = 2_048;
    public static final int CORRECTION_REASON_MAX_LENGTH = 500;
    private static final Pattern EVIDENCE_URI =
            Pattern.compile("^(https|s3|gs|az)://[^\\s]+$");

    public ActivityLog {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(activityId, "activityId is required");
        Objects.requireNonNull(employeeId, "employeeId is required");
        Objects.requireNonNull(authorProfileId, "authorProfileId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        notes = optionalText(notes, "notes", NOTES_MAX_LENGTH);
        quantity = optionalQuantity(quantity);
        unit = Objects.requireNonNull(unit, "unit is required");
        evidenceUri = optionalEvidenceUri(evidenceUri);
        correctsLogId = Objects.requireNonNull(correctsLogId, "correctsLogId is required");
        correctionKind = Objects.requireNonNull(correctionKind, "correctionKind is required");
        correctionReason = optionalText(
                correctionReason, "correctionReason", CORRECTION_REASON_MAX_LENGTH);
        requireQuantityUnit(quantity, unit);
        validatePayload(
                notes, quantity, unit, evidenceUri,
                correctsLogId.isPresent(), correctionKind, correctionReason);
        if (correctsLogId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("activity log cannot correct itself");
        }
    }

    public static Optional<String> optionalText(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required").map(text -> {
            String normalized = text.strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            if (normalized.length() > maxLength) {
                throw new IllegalArgumentException(
                        fieldName + " must not exceed " + maxLength + " characters");
            }
            return normalized;
        });
    }

    public static Optional<BigDecimal> optionalQuantity(Optional<BigDecimal> value) {
        return Objects.requireNonNull(value, "quantity is required").map(quantity -> {
            BigDecimal normalized = Objects.requireNonNull(quantity, "quantity value is required")
                    .stripTrailingZeros();
            if (normalized.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
            if (normalized.scale() > 4 || integerDigits > 14) {
                throw new IllegalArgumentException("quantity exceeds NUMERIC(18,4)");
            }
            return normalized;
        });
    }

    public static Optional<String> optionalEvidenceUri(Optional<String> value) {
        Optional<String> normalized = optionalText(
                value, "evidenceUri", EVIDENCE_URI_MAX_LENGTH);
        normalized.ifPresent(uri -> {
            if (!EVIDENCE_URI.matcher(uri).matches()) {
                throw new IllegalArgumentException("evidenceUri scheme or format is not allowed");
            }
        });
        return normalized;
    }

    private static void requireQuantityUnit(
            Optional<BigDecimal> quantity,
            Optional<ActivityLogUnit> unit) {
        if (quantity.isPresent() != unit.isPresent()) {
            throw new IllegalArgumentException("quantity and unit must be provided together");
        }
    }

    public static void validatePayload(
            Optional<String> notes,
            Optional<BigDecimal> quantity,
            Optional<ActivityLogUnit> unit,
            Optional<String> evidenceUri,
            boolean correction,
            Optional<ActivityLogCorrectionKind> correctionKind,
            Optional<String> correctionReason) {
        if (correction != correctionKind.isPresent() || correction != correctionReason.isPresent()) {
            throw new IllegalArgumentException("correction target, kind and reason must be provided together");
        }
        if (correctionKind.filter(kind -> kind == ActivityLogCorrectionKind.VOID).isPresent()
                && (quantity.isPresent() || unit.isPresent() || evidenceUri.isPresent())) {
            throw new IllegalArgumentException("void correction cannot carry quantity, unit or evidence");
        }
        boolean hasPayload = notes.isPresent() || quantity.isPresent() || evidenceUri.isPresent();
        if (correctionKind.orElse(null) != ActivityLogCorrectionKind.VOID && !hasPayload) {
            throw new IllegalArgumentException("activity log requires notes, quantity or evidence");
        }
    }
}
