package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class HarvestCommands {

    private HarvestCommands() {
    }

    public record Post(
            UUID farmId, UUID fieldId, UUID seasonId, UUID cropId, LocalDate occurredOn,
            BigDecimal quantityKg, BigDecimal wasteQuantityKg,
            Optional<String> qualityGrade, Optional<BigDecimal> revenueVnd,
            TenantAuditMetadata audit) {

        public Post {
            Objects.requireNonNull(farmId, "farmId is required");
            Objects.requireNonNull(fieldId, "fieldId is required");
            Objects.requireNonNull(seasonId, "seasonId is required");
            Objects.requireNonNull(cropId, "cropId is required");
            Objects.requireNonNull(occurredOn, "occurredOn is required");
            quantityKg = Harvest.positiveKilograms(quantityKg, "quantityKg");
            wasteQuantityKg = Harvest.bounded(wasteQuantityKg, "wasteQuantityKg", 15, 3);
            qualityGrade = Harvest.optionalText(
                    qualityGrade, "qualityGrade", Harvest.QUALITY_GRADE_MAX_LENGTH);
            revenueVnd = Objects.requireNonNull(revenueVnd, "revenueVnd is required")
                    .map(value -> Harvest.bounded(value, "revenueVnd", 17, 2));
            Harvest.validatePayload(
                    quantityKg, wasteQuantityKg, qualityGrade, revenueVnd,
                    false, Optional.empty(), Optional.empty());
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Correct(
            HarvestCorrectionKind correctionKind, LocalDate occurredOn,
            BigDecimal quantityKg, BigDecimal wasteQuantityKg,
            Optional<String> qualityGrade, Optional<BigDecimal> revenueVnd,
            String correctionReason, TenantAuditMetadata audit) {

        public Correct {
            Objects.requireNonNull(correctionKind, "correctionKind is required");
            Objects.requireNonNull(occurredOn, "occurredOn is required");
            quantityKg = Harvest.bounded(quantityKg, "quantityKg", 15, 3);
            wasteQuantityKg = Harvest.bounded(wasteQuantityKg, "wasteQuantityKg", 15, 3);
            qualityGrade = Harvest.optionalText(
                    qualityGrade, "qualityGrade", Harvest.QUALITY_GRADE_MAX_LENGTH);
            revenueVnd = Objects.requireNonNull(revenueVnd, "revenueVnd is required")
                    .map(value -> Harvest.bounded(value, "revenueVnd", 17, 2));
            correctionReason = Harvest.optionalText(
                    Optional.ofNullable(correctionReason), "correctionReason",
                    Harvest.CORRECTION_REASON_MAX_LENGTH).orElseThrow();
            Harvest.validatePayload(
                    quantityKg, wasteQuantityKg, qualityGrade, revenueVnd,
                    true, Optional.of(correctionKind), Optional.of(correctionReason));
            Objects.requireNonNull(audit, "audit is required");
        }
    }
}
