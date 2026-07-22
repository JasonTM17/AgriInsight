package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Objects;
import java.util.UUID;

public final class WarehouseAssignmentCommands {

    private WarehouseAssignmentCommands() {
    }

    public record Grant(
            UUID userProfileId,
            UUID warehouseId,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Grant {
            Objects.requireNonNull(userProfileId, "userProfileId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Revoke(long expectedVersion, TenantAuditMetadata audit) {

        public Revoke {
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
