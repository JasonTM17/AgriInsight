package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Field;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record FieldUpdateRequest(
        @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @Size(max = 200) String displayName,
        @Positive @Digits(integer = 10, fraction = 4) BigDecimal areaHectares,
        UUID responsibleEmployeeId,
        Boolean clearResponsibleEmployeeId,
        @Digits(integer = 3, fraction = 6)
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Digits(integer = 3, fraction = 6)
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        Boolean clearCoordinates,
        @Size(max = 120) String soilType,
        Boolean clearSoilType,
        @Size(max = 120) String irrigationType,
        Boolean clearIrrigationType,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FieldUpdateRequest {
        code = code == null ? null : Field.canonicalCode(code);
        displayName = displayName == null ? null : Field.canonicalDisplayName(displayName);
        areaHectares = areaHectares == null ? null : Field.positiveArea(areaHectares);
        clearResponsibleEmployeeId = Boolean.TRUE.equals(clearResponsibleEmployeeId);
        clearCoordinates = Boolean.TRUE.equals(clearCoordinates);
        clearSoilType = Boolean.TRUE.equals(clearSoilType);
        clearIrrigationType = Boolean.TRUE.equals(clearIrrigationType);
        FieldCreateRequest.requireCoordinatePair(latitude, longitude);
        if (latitude != null) {
            Field.Coordinates coordinates = new Field.Coordinates(latitude, longitude);
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
        }
        soilType = FieldCreateRequest.normalizeText(soilType, "soilType");
        irrigationType = FieldCreateRequest.normalizeText(irrigationType, "irrigationType");
        reasonCode = FieldCreateRequest.normalizeReason(reasonCode);
        rejectSetAndClear(responsibleEmployeeId, clearResponsibleEmployeeId, "responsibleEmployeeId");
        rejectSetAndClear(latitude, clearCoordinates, "coordinates");
        rejectSetAndClear(soilType, clearSoilType, "soilType");
        rejectSetAndClear(irrigationType, clearIrrigationType, "irrigationType");
        if (code == null && displayName == null && areaHectares == null
                && responsibleEmployeeId == null && !clearResponsibleEmployeeId
                && latitude == null && !clearCoordinates && soilType == null && !clearSoilType
                && irrigationType == null && !clearIrrigationType) {
            throw new IllegalArgumentException("at least one field value must be provided");
        }
    }

    public Optional<Field.Coordinates> coordinates() {
        return latitude == null ? Optional.empty()
                : Optional.of(new Field.Coordinates(latitude, longitude));
    }

    private static void rejectSetAndClear(Object value, boolean clear, String fieldName) {
        if (value != null && clear) {
            throw new IllegalArgumentException(fieldName + " cannot be set and cleared together");
        }
    }
}
