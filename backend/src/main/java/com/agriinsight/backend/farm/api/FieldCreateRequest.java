package com.agriinsight.backend.farm.api;

import com.agriinsight.backend.farm.domain.Field;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record FieldCreateRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,63}") String code,
        @NotBlank @Size(max = 200) String displayName,
        @NotNull @Positive @Digits(integer = 10, fraction = 4) BigDecimal areaHectares,
        UUID responsibleEmployeeId,
        @Digits(integer = 3, fraction = 6)
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @Digits(integer = 3, fraction = 6)
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Size(max = 120) String soilType,
        @Size(max = 120) String irrigationType,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}") String reasonCode) {

    public FieldCreateRequest {
        code = code == null ? null : Field.canonicalCode(code);
        displayName = displayName == null ? null : Field.canonicalDisplayName(displayName);
        areaHectares = areaHectares == null ? null : Field.positiveArea(areaHectares);
        requireCoordinatePair(latitude, longitude);
        if (latitude != null) {
            Field.Coordinates coordinates = new Field.Coordinates(latitude, longitude);
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
        }
        soilType = normalizeText(soilType, "soilType");
        irrigationType = normalizeText(irrigationType, "irrigationType");
        reasonCode = normalizeReason(reasonCode);
    }

    public Optional<Field.Coordinates> coordinates() {
        return latitude == null ? Optional.empty()
                : Optional.of(new Field.Coordinates(latitude, longitude));
    }

    static void requireCoordinatePair(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
    }

    static String normalizeText(String value, String fieldName) {
        return value == null ? null
                : Field.optionalText(Optional.of(value), fieldName, 120).orElseThrow();
    }

    static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
