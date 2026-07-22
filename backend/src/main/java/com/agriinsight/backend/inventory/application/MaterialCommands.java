package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class MaterialCommands {

    private MaterialCommands() {
    }

    public record Create(
            String code,
            String displayName,
            CanonicalUnit baseUnit,
            Optional<BigDecimal> minimumStockQuantity,
            TenantAuditMetadata audit) {

        public Create {
            code = Material.canonicalCode(code);
            displayName = Material.canonicalDisplayName(displayName);
            Objects.requireNonNull(baseUnit, "baseUnit is required");
            minimumStockQuantity = Material.optionalMinimumStock(minimumStockQuantity);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<CanonicalUnit> baseUnit,
            Optional<Optional<BigDecimal>> minimumStockQuantity,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required").map(Material::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Material::canonicalDisplayName);
            baseUnit = Objects.requireNonNull(baseUnit, "baseUnit is required");
            minimumStockQuantity = Objects.requireNonNull(
                    minimumStockQuantity, "minimumStockQuantity is required")
                    .map(Material::optionalMinimumStock);
            if (code.isEmpty() && displayName.isEmpty() && baseUnit.isEmpty()
                    && minimumStockQuantity.isEmpty()) {
                throw new IllegalArgumentException("at least one material field must be provided");
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
