package com.agriinsight.backend.operations.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Harvest(
        UUID id,
        UUID tenantId,
        UUID farmId,
        UUID fieldId,
        UUID seasonId,
        UUID cropId,
        UUID recordedByProfileId,
        LocalDate occurredOn,
        BigDecimal quantityKg,
        BigDecimal wasteQuantityKg,
        Optional<String> qualityGrade,
        Optional<BigDecimal> revenueVnd,
        Optional<UUID> correctsHarvestId,
        Optional<HarvestCorrectionKind> correctionKind,
        Optional<String> correctionReason) {

    public static final int QUALITY_GRADE_MAX_LENGTH = 64;
    public static final int CORRECTION_REASON_MAX_LENGTH = 500;

    public Harvest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        Objects.requireNonNull(cropId, "cropId is required");
        Objects.requireNonNull(recordedByProfileId, "recordedByProfileId is required");
        Objects.requireNonNull(occurredOn, "occurredOn is required");
        quantityKg = bounded(quantityKg, "quantityKg", 15, 3);
        wasteQuantityKg = bounded(wasteQuantityKg, "wasteQuantityKg", 15, 3);
        qualityGrade = optionalText(qualityGrade, "qualityGrade", QUALITY_GRADE_MAX_LENGTH);
        revenueVnd = Objects.requireNonNull(revenueVnd, "revenueVnd is required")
                .map(value -> bounded(value, "revenueVnd", 17, 2));
        correctsHarvestId = Objects.requireNonNull(correctsHarvestId, "correctsHarvestId is required");
        correctionKind = Objects.requireNonNull(correctionKind, "correctionKind is required");
        correctionReason = optionalText(
                correctionReason, "correctionReason", CORRECTION_REASON_MAX_LENGTH);
        validatePayload(
                quantityKg, wasteQuantityKg, qualityGrade, revenueVnd,
                correctsHarvestId.isPresent(), correctionKind, correctionReason);
        if (correctsHarvestId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("harvest cannot correct itself");
        }
    }

    public static BigDecimal positiveKilograms(BigDecimal value, String fieldName) {
        BigDecimal normalized = bounded(value, fieldName, 15, 3);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return normalized;
    }

    public static BigDecimal bounded(
            BigDecimal value,
            String fieldName,
            int integerDigits,
            int fractionDigits) {
        BigDecimal required = Objects.requireNonNull(value, fieldName + " is required");
        if (required.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        BigDecimal normalized = required.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int wholeDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (scale > fractionDigits || wholeDigits > integerDigits) {
            throw new IllegalArgumentException(fieldName + " exceeds supported precision");
        }
        return new BigDecimal(normalized.toPlainString());
    }

    public static Optional<String> optionalText(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required").map(text -> {
            String normalized = Objects.requireNonNull(text, fieldName + " value is required").strip();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            if (normalized.length() > maxLength) {
                throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
            }
            return normalized;
        });
    }

    public static void validatePayload(
            BigDecimal quantityKg,
            BigDecimal wasteQuantityKg,
            Optional<String> qualityGrade,
            Optional<BigDecimal> revenueVnd,
            boolean correction,
            Optional<HarvestCorrectionKind> correctionKind,
            Optional<String> correctionReason) {
        if (correction != correctionKind.isPresent() || correction != correctionReason.isPresent()) {
            throw new IllegalArgumentException("correction target, kind and reason must be provided together");
        }
        if (correctionKind.filter(kind -> kind == HarvestCorrectionKind.VOID).isPresent()) {
            if (quantityKg.signum() != 0 || wasteQuantityKg.signum() != 0
                    || qualityGrade.isPresent() || revenueVnd.isPresent()) {
                throw new IllegalArgumentException("void correction cannot carry harvest values");
            }
            return;
        }
        positiveKilograms(quantityKg, "quantityKg");
        if (wasteQuantityKg.compareTo(quantityKg) > 0) {
            throw new IllegalArgumentException("wasteQuantityKg must not exceed quantityKg");
        }
    }
}
