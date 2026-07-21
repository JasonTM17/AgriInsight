package com.agriinsight.backend.farm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Season(
        UUID id,
        UUID tenantId,
        UUID farmId,
        UUID fieldId,
        UUID cropId,
        String code,
        String displayName,
        Optional<String> varietyName,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        BigDecimal plantedAreaHectares,
        Optional<BigDecimal> budgetVnd) {

    public Season {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(cropId, "cropId is required");
        code = canonicalCode(code);
        displayName = canonicalDisplayName(displayName);
        varietyName = optionalText(varietyName, "varietyName", 200);
        requireDateRange(plannedStartDate, plannedEndDate);
        plantedAreaHectares = positiveDecimal(plantedAreaHectares, "plantedAreaHectares", 10, 4);
        budgetVnd = optionalNonnegativeDecimal(budgetVnd, "budgetVnd", 17, 2);
    }

    public static String canonicalCode(String value) {
        return Farm.canonicalCode(value);
    }

    public static String canonicalDisplayName(String value) {
        return Farm.canonicalDisplayName(value);
    }

    public static Optional<String> optionalText(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required")
                .map(text -> requiredText(text, fieldName, maxLength));
    }

    public static void requireDateRange(LocalDate start, LocalDate end) {
        LocalDate requiredStart = Objects.requireNonNull(start, "plannedStartDate is required");
        LocalDate requiredEnd = Objects.requireNonNull(end, "plannedEndDate is required");
        if (requiredEnd.isBefore(requiredStart)) {
            throw new IllegalArgumentException("plannedEndDate must not be before plannedStartDate");
        }
    }

    public static BigDecimal positiveArea(BigDecimal value) {
        return positiveDecimal(value, "plantedAreaHectares", 10, 4);
    }

    public static Optional<BigDecimal> optionalBudget(Optional<BigDecimal> value) {
        return optionalNonnegativeDecimal(value, "budgetVnd", 17, 2);
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        String normalized = Objects.requireNonNull(value, fieldName + " is required").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static BigDecimal positiveDecimal(
            BigDecimal value,
            String fieldName,
            int integerDigits,
            int fractionDigits) {
        BigDecimal required = Objects.requireNonNull(value, fieldName + " is required");
        if (required.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return boundedDecimal(required, fieldName, integerDigits, fractionDigits);
    }

    private static Optional<BigDecimal> optionalNonnegativeDecimal(
            Optional<BigDecimal> value,
            String fieldName,
            int integerDigits,
            int fractionDigits) {
        return Objects.requireNonNull(value, fieldName + " is required").map(decimal -> {
            if (decimal.signum() < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative");
            }
            return boundedDecimal(decimal, fieldName, integerDigits, fractionDigits);
        });
    }

    private static BigDecimal boundedDecimal(
            BigDecimal value,
            String fieldName,
            int integerDigits,
            int fractionDigits) {
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int wholeDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (scale > fractionDigits || wholeDigits > integerDigits) {
            throw new IllegalArgumentException(fieldName + " exceeds supported precision");
        }
        return new BigDecimal(normalized.toPlainString());
    }

    public enum Status {
        PLANNED,
        ACTIVE,
        COMPLETED,
        CANCELLED;

        public boolean canTransitionTo(Status target) {
            Objects.requireNonNull(target, "targetStatus is required");
            return switch (this) {
                case PLANNED -> target == ACTIVE || target == CANCELLED;
                case ACTIVE -> target == COMPLETED || target == CANCELLED;
                case COMPLETED, CANCELLED -> false;
            };
        }

        public boolean terminal() {
            return this == COMPLETED || this == CANCELLED;
        }
    }
}
