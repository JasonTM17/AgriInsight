package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.SupplierRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresSupplierLifecycleStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresSupplierMutationStore mutations;

    PostgresSupplierLifecycleStore(
            JdbcTemplate jdbcTemplate,
            PostgresSupplierMutationStore mutations) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = Objects.requireNonNull(mutations, "mutations is required");
    }

    Optional<SupplierRecord> updateActive(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Supplier");
        UUID target = Objects.requireNonNull(supplierId, "supplierId is required");
        requireVersion(expectedVersion);
        if (!lockSupplier(tenantScope, target)) {
            return Optional.empty();
        }
        if (!active && mutations.hasReferences(tenantScope, target)) {
            return Optional.empty();
        }
        return SupplierRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE suppliers AS supplier
                   SET active = ?, version = supplier.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE supplier.tenant_id = ? AND supplier.id = ?
                   AND supplier.version = ? AND supplier.active <> ?
                RETURNING %s
                """.formatted(SupplierRowMapping.RETURNING_COLUMNS),
                SupplierRowMapping.MAPPER,
                active, tenantScope.tenantId(), target, expectedVersion, active));
    }

    private boolean lockSupplier(ScopeContext scope, UUID supplierId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT supplier.id FROM suppliers AS supplier
                 WHERE supplier.tenant_id = ? AND supplier.id = ? FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), supplierId);
        return SupplierRowMapping.exactlyOneOrEmpty(rows).isPresent();
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
