package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.application.FieldRecord;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record FieldResponse(
        UUID id,
        UUID farmId,
        String code,
        String displayName,
        BigDecimal areaHectares,
        Optional<UUID> responsibleEmployeeId,
        Optional<BigDecimal> latitude,
        Optional<BigDecimal> longitude,
        Optional<String> soilType,
        Optional<String> irrigationType,
        boolean active,
        long version) {

    public FieldResponse {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(farmId, "farmId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(areaHectares, "areaHectares is required");
        Objects.requireNonNull(responsibleEmployeeId, "responsibleEmployeeId is required");
        Objects.requireNonNull(latitude, "latitude is required");
        Objects.requireNonNull(longitude, "longitude is required");
        Objects.requireNonNull(soilType, "soilType is required");
        Objects.requireNonNull(irrigationType, "irrigationType is required");
    }

    public static FieldResponse from(FieldRecord field) {
        Objects.requireNonNull(field, "field is required");
        return new FieldResponse(
                field.id(), field.farmId(), field.code(), field.displayName(), field.areaHectares(),
                field.responsibleEmployeeId(),
                field.coordinates().map(coordinates -> coordinates.latitude()),
                field.coordinates().map(coordinates -> coordinates.longitude()),
                field.soilType(), field.irrigationType(), field.active(), field.version());
    }
}
