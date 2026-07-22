package com.agriinsight.backend.cost.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OperatingCostEntry(
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
        UUID recordedByProfileId) {

    public static final int DESCRIPTION_MAX_LENGTH = 1000;
    public static final int SOURCE_REFERENCE_MAX_LENGTH = 200;

    public OperatingCostEntry {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(category, "category is required");
        amountVnd = positiveVnd(amountVnd);
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        description = optionalText(description, "description", DESCRIPTION_MAX_LENGTH);
        sourceReference = optionalText(
                sourceReference, "sourceReference", SOURCE_REFERENCE_MAX_LENGTH);
        reversalOf = Objects.requireNonNull(reversalOf, "reversalOf is required");
        if ((kind == CostEntryKind.REVERSAL) != reversalOf.isPresent()) {
            throw new IllegalArgumentException(
                    "Only reversal entries require an original entry id");
        }
        if (reversalOf.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("An operating cost entry cannot reverse itself");
        }
        Objects.requireNonNull(commandReference, "commandReference is required");
        Objects.requireNonNull(recordedByProfileId, "recordedByProfileId is required");
    }

    public static BigDecimal positiveVnd(BigDecimal value) {
        BigDecimal required = Objects.requireNonNull(value, "amountVnd is required");
        BigDecimal normalized = required.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int wholeDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (required.signum() <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        if (scale > 2 || wholeDigits > 17) {
            throw new IllegalArgumentException("amountVnd exceeds supported VND precision");
        }
        return new BigDecimal(normalized.toPlainString());
    }

    public static Optional<String> optionalText(
            Optional<String> value, String fieldName, int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required").map(text -> {
            String normalized = Objects.requireNonNull(
                    text, fieldName + " value is required").strip();
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

}
