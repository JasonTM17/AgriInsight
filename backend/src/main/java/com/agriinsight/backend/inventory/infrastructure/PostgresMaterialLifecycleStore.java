package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.MaterialRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialLifecycleStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresMaterialMutationStore mutations;

    PostgresMaterialLifecycleStore(
            JdbcTemplate jdbcTemplate,
            PostgresMaterialMutationStore mutations) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = Objects.requireNonNull(mutations, "mutations is required");
    }

    Optional<MaterialRecord> updateActive(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Material");
        UUID target = Objects.requireNonNull(materialId, "materialId is required");
        requireVersion(expectedVersion);
        if (!lockMaterial(tenantScope, target)) {
            return Optional.empty();
        }
        if (!active && mutations.hasReferences(tenantScope, target)) {
            return Optional.empty();
        }
        return MaterialRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE materials AS material
                   SET active = ?, version = material.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE material.tenant_id = ? AND material.id = ?
                   AND material.version = ? AND material.active <> ?
                RETURNING %s
                """.formatted(MaterialRowMapping.RETURNING_COLUMNS),
                MaterialRowMapping.MAPPER,
                active, tenantScope.tenantId(), target, expectedVersion, active));
    }

    private boolean lockMaterial(ScopeContext scope, UUID materialId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT material.id FROM materials AS material
                 WHERE material.tenant_id = ? AND material.id = ? FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), materialId);
        return MaterialRowMapping.exactlyOneOrEmpty(rows).isPresent();
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
