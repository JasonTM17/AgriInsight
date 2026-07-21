package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.domain.Field;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FieldCommands {

    private FieldCommands() {
    }

    public record Create(
            UUID farmId,
            String code,
            String displayName,
            BigDecimal areaHectares,
            Optional<UUID> responsibleEmployeeId,
            Optional<Field.Coordinates> coordinates,
            Optional<String> soilType,
            Optional<String> irrigationType,
            TenantAuditMetadata audit) {

        public Create {
            Objects.requireNonNull(farmId, "farmId is required");
            code = Field.canonicalCode(code);
            displayName = Field.canonicalDisplayName(displayName);
            areaHectares = Field.positiveArea(areaHectares);
            responsibleEmployeeId = Field.optionalId(responsibleEmployeeId, "responsibleEmployeeId");
            coordinates = Objects.requireNonNull(coordinates, "coordinates is required");
            soilType = Field.optionalText(soilType, "soilType", 120);
            irrigationType = Field.optionalText(irrigationType, "irrigationType", 120);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<BigDecimal> areaHectares,
            Optional<Optional<UUID>> responsibleEmployeeId,
            Optional<Optional<Field.Coordinates>> coordinates,
            Optional<Optional<String>> soilType,
            Optional<Optional<String>> irrigationType,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Field::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Field::canonicalDisplayName);
            areaHectares = Objects.requireNonNull(areaHectares, "areaHectares is required")
                    .map(Field::positiveArea);
            responsibleEmployeeId = normalizeIdPatch(responsibleEmployeeId);
            coordinates = Objects.requireNonNull(coordinates, "coordinates is required");
            soilType = normalizeTextPatch(soilType, "soilType");
            irrigationType = normalizeTextPatch(irrigationType, "irrigationType");
            if (code.isEmpty() && displayName.isEmpty() && areaHectares.isEmpty()
                    && responsibleEmployeeId.isEmpty() && coordinates.isEmpty()
                    && soilType.isEmpty() && irrigationType.isEmpty()) {
                throw new IllegalArgumentException("at least one field value must be provided");
            }
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }

        private static Optional<Optional<UUID>> normalizeIdPatch(Optional<Optional<UUID>> patch) {
            return Objects.requireNonNull(patch, "responsibleEmployeeId is required")
                    .map(value -> Field.optionalId(value, "responsibleEmployeeId"));
        }

        private static Optional<Optional<String>> normalizeTextPatch(
                Optional<Optional<String>> patch,
                String fieldName) {
            return Objects.requireNonNull(patch, fieldName + " is required")
                    .map(value -> Field.optionalText(value, fieldName, 120));
        }
    }

    public record Lifecycle(long expectedVersion, TenantAuditMetadata audit) {

        public Lifecycle {
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
