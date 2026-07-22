package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.HarvestRecord;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record HarvestResponse(
        UUID id, UUID farmId, UUID fieldId, UUID seasonId, UUID cropId,
        UUID recordedByProfileId, LocalDate occurredOn,
        BigDecimal quantityKg, BigDecimal wasteQuantityKg,
        Optional<String> qualityGrade, Optional<BigDecimal> revenueVnd,
        Optional<UUID> correctsHarvestId, Optional<HarvestCorrectionKind> correctionKind,
        Optional<String> correctionReason, long version) {

    public HarvestResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(fieldId, "fieldId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        Objects.requireNonNull(cropId, "cropId is required");
        Objects.requireNonNull(recordedByProfileId, "recordedByProfileId is required");
        Objects.requireNonNull(occurredOn, "occurredOn is required");
        Objects.requireNonNull(quantityKg, "quantityKg is required");
        Objects.requireNonNull(wasteQuantityKg, "wasteQuantityKg is required");
        Objects.requireNonNull(qualityGrade, "qualityGrade is required");
        Objects.requireNonNull(revenueVnd, "revenueVnd is required");
        Objects.requireNonNull(correctsHarvestId, "correctsHarvestId is required");
        Objects.requireNonNull(correctionKind, "correctionKind is required");
        Objects.requireNonNull(correctionReason, "correctionReason is required");
    }

    public static HarvestResponse from(HarvestRecord harvest) {
        Objects.requireNonNull(harvest, "harvest is required");
        return new HarvestResponse(
                harvest.id(), harvest.farmId(), harvest.fieldId(), harvest.seasonId(), harvest.cropId(),
                harvest.recordedByProfileId(), harvest.occurredOn(), harvest.quantityKg(),
                harvest.wasteQuantityKg(), harvest.qualityGrade(), harvest.revenueVnd(),
                harvest.correctsHarvestId(), harvest.correctionKind(), harvest.correctionReason(),
                harvest.version());
    }
}
