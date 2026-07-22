package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.domain.Supplier;
import java.util.Objects;
import java.util.Optional;

public final class SupplierCommands {

    private SupplierCommands() {
    }

    public record Create(
            String code,
            String displayName,
            TenantAuditMetadata audit) {

        public Create {
            code = Supplier.canonicalCode(code);
            displayName = Supplier.canonicalDisplayName(displayName);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Supplier::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Supplier::canonicalDisplayName);
            if (code.isEmpty() && displayName.isEmpty()) {
                throw new IllegalArgumentException("at least one supplier field must be provided");
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
