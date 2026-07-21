package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.farm.domain.Field;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record FieldRecord(
        UUID id,
        UUID tenantId,
        UUID farmId,
        String code,
        String displayName,
        BigDecimal areaHectares,
        Optional<UUID> responsibleEmployeeId,
        Optional<Field.Coordinates> coordinates,
        Optional<String> soilType,
        Optional<String> irrigationType,
        boolean active,
        long version) {

    public FieldRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        code = Field.canonicalCode(code);
        displayName = Field.canonicalDisplayName(displayName);
        areaHectares = Field.positiveArea(areaHectares);
        responsibleEmployeeId = Field.optionalId(responsibleEmployeeId, "responsibleEmployeeId");
        coordinates = Objects.requireNonNull(coordinates, "coordinates is required");
        soilType = Field.optionalText(soilType, "soilType", 120);
        irrigationType = Field.optionalText(irrigationType, "irrigationType", 120);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
