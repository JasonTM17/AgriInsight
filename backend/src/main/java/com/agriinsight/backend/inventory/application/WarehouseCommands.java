package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.domain.Warehouse;
import java.util.Objects;
import java.util.Optional;

public final class WarehouseCommands {

    private WarehouseCommands() {
    }

    public record Create(
            String code,
            String displayName,
            Optional<String> locationText,
            TenantAuditMetadata audit) {

        public Create {
            code = Warehouse.canonicalCode(code);
            displayName = Warehouse.canonicalDisplayName(displayName);
            locationText = Objects.requireNonNull(locationText, "locationText is required")
                    .map(Warehouse::canonicalLocation);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> locationText,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Warehouse::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Warehouse::canonicalDisplayName);
            locationText = Objects.requireNonNull(locationText, "locationText is required")
                    .map(value -> value.map(Warehouse::canonicalLocation));
            if (code.isEmpty() && displayName.isEmpty() && locationText.isEmpty()) {
                throw new IllegalArgumentException("at least one warehouse field must be provided");
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
