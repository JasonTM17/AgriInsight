package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SeasonCommands {

    private SeasonCommands() {
    }

    public record Create(
            UUID farmId,
            UUID fieldId,
            UUID cropId,
            String code,
            String displayName,
            Optional<String> varietyName,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            BigDecimal plantedAreaHectares,
            Optional<BigDecimal> budgetVnd,
            TenantAuditMetadata audit) {

        public Create {
            Objects.requireNonNull(farmId, "farmId is required");
            Objects.requireNonNull(fieldId, "fieldId is required");
            Objects.requireNonNull(cropId, "cropId is required");
            code = Season.canonicalCode(code);
            displayName = Season.canonicalDisplayName(displayName);
            varietyName = Season.optionalText(
                    varietyName, "varietyName", Season.VARIETY_NAME_MAX_LENGTH);
            Season.requireDateRange(plannedStartDate, plannedEndDate);
            plantedAreaHectares = Season.positiveArea(plantedAreaHectares);
            budgetVnd = Season.optionalBudget(budgetVnd);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> varietyName,
            Optional<LocalDate> plannedStartDate,
            Optional<LocalDate> plannedEndDate,
            Optional<BigDecimal> plantedAreaHectares,
            Optional<Optional<BigDecimal>> budgetVnd,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Season::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Season::canonicalDisplayName);
            varietyName = normalizeOptionalTextPatch(varietyName);
            plannedStartDate = Objects.requireNonNull(plannedStartDate, "plannedStartDate is required");
            plannedEndDate = Objects.requireNonNull(plannedEndDate, "plannedEndDate is required");
            plantedAreaHectares = Objects.requireNonNull(plantedAreaHectares, "plantedAreaHectares is required")
                    .map(Season::positiveArea);
            budgetVnd = normalizeBudgetPatch(budgetVnd);
            if (code.isEmpty() && displayName.isEmpty() && varietyName.isEmpty()
                    && plannedStartDate.isEmpty() && plannedEndDate.isEmpty()
                    && plantedAreaHectares.isEmpty() && budgetVnd.isEmpty()) {
                throw new IllegalArgumentException("at least one season field must be provided");
            }
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }

        private static Optional<Optional<String>> normalizeOptionalTextPatch(
                Optional<Optional<String>> patch) {
            return Objects.requireNonNull(patch, "varietyName is required")
                    .map(value -> Season.optionalText(
                            value, "varietyName", Season.VARIETY_NAME_MAX_LENGTH));
        }

        private static Optional<Optional<BigDecimal>> normalizeBudgetPatch(
                Optional<Optional<BigDecimal>> patch) {
            return Objects.requireNonNull(patch, "budgetVnd is required")
                    .map(Season::optionalBudget);
        }
    }

    public record Transition(
            Season.Status targetStatus,
            LocalDate effectiveDate,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Transition {
            Objects.requireNonNull(targetStatus, "targetStatus is required");
            Objects.requireNonNull(effectiveDate, "effectiveDate is required");
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    private static void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
