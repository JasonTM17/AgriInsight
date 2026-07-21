package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.farm.domain.Crop;
import java.util.Objects;
import java.util.Optional;

public final class CropCommands {

    private CropCommands() {
    }

    public record Create(
            String code,
            String displayName,
            Optional<String> scientificName,
            TenantAuditMetadata audit) {

        public Create {
            code = Crop.canonicalCode(code);
            displayName = Crop.canonicalDisplayName(displayName);
            scientificName = Crop.optionalScientificName(scientificName);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> scientificName,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Crop::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Crop::canonicalDisplayName);
            scientificName = Objects.requireNonNull(scientificName, "scientificName is required")
                    .map(Crop::optionalScientificName);
            if (code.isEmpty() && displayName.isEmpty() && scientificName.isEmpty()) {
                throw new IllegalArgumentException("at least one crop value must be provided");
            }
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
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
