package com.agriinsight.backend.farm.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Field(
        UUID id,
        UUID tenantId,
        UUID farmId,
        String code,
        String displayName,
        BigDecimal areaHectares,
        Optional<UUID> responsibleEmployeeId,
        Optional<Coordinates> coordinates,
        Optional<String> soilType,
        Optional<String> irrigationType) {

    public Field {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(farmId, "farmId is required");
        code = canonicalCode(code);
        displayName = canonicalDisplayName(displayName);
        areaHectares = positiveArea(areaHectares);
        responsibleEmployeeId = optionalId(responsibleEmployeeId, "responsibleEmployeeId");
        coordinates = Objects.requireNonNull(coordinates, "coordinates is required");
        soilType = optionalText(soilType, "soilType", 120);
        irrigationType = optionalText(irrigationType, "irrigationType", 120);
    }

    public static String canonicalCode(String value) {
        return Farm.canonicalCode(value);
    }

    public static String canonicalDisplayName(String value) {
        return Farm.canonicalDisplayName(value);
    }

    public static BigDecimal positiveArea(BigDecimal value) {
        BigDecimal required = Objects.requireNonNull(value, "areaHectares is required");
        if (required.signum() <= 0) {
            throw new IllegalArgumentException("areaHectares must be positive");
        }
        return boundedDecimal(required, "areaHectares", 10, 4);
    }

    public static Optional<String> optionalText(
            Optional<String> value,
            String fieldName,
            int maxLength) {
        return Objects.requireNonNull(value, fieldName + " is required")
                .map(text -> requiredText(text, fieldName, maxLength));
    }

    public static Optional<UUID> optionalId(Optional<UUID> value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
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

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

        public Coordinates {
            latitude = boundedDecimal(
                    Objects.requireNonNull(latitude, "latitude is required"), "latitude", 3, 6);
            longitude = boundedDecimal(
                    Objects.requireNonNull(longitude, "longitude is required"), "longitude", 3, 6);
            if (latitude.compareTo(new BigDecimal("-90")) < 0
                    || latitude.compareTo(new BigDecimal("90")) > 0) {
                throw new IllegalArgumentException("latitude must be between -90 and 90");
            }
            if (longitude.compareTo(new BigDecimal("-180")) < 0
                    || longitude.compareTo(new BigDecimal("180")) > 0) {
                throw new IllegalArgumentException("longitude must be between -180 and 180");
            }
        }
    }
}
