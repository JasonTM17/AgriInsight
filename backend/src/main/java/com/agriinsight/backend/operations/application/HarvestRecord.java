package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public record HarvestRecord(
        UUID id, UUID tenantId, UUID farmId, UUID fieldId, UUID seasonId, UUID cropId,
        UUID recordedByProfileId, LocalDate occurredOn,
        BigDecimal quantityKg, BigDecimal wasteQuantityKg,
        Optional<String> qualityGrade, Optional<BigDecimal> revenueVnd,
        Optional<UUID> correctsHarvestId, Optional<HarvestCorrectionKind> correctionKind,
        Optional<String> correctionReason, long version) {

    public HarvestRecord {
        new Harvest(
                id, tenantId, farmId, fieldId, seasonId, cropId, recordedByProfileId,
                occurredOn, quantityKg, wasteQuantityKg, qualityGrade, revenueVnd,
                correctsHarvestId, correctionKind, correctionReason);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
