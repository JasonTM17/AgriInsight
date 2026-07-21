package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

final class PostgresFarmLifecycleStore {

    private static final String COLUMNS = "id, tenant_id, code, display_name, active, version";
    private static final String ACTIVE_DEPENDENT_PREDICATE = """
            EXISTS (
                SELECT 1 FROM fields AS child_field
                 WHERE child_field.tenant_id = farm.tenant_id
                   AND child_field.farm_id = farm.id
                   AND child_field.active
            ) OR EXISTS (
                SELECT 1 FROM seasons AS season
                 WHERE season.tenant_id = farm.tenant_id
                   AND season.farm_id = farm.id
                   AND season.status IN ('PLANNED', 'ACTIVE')
            ) OR EXISTS (
                SELECT 1 FROM activities AS activity
                 WHERE activity.tenant_id = farm.tenant_id
                   AND activity.farm_id = farm.id
                   AND activity.status IN ('PLANNED', 'STARTED')
            ) OR EXISTS (
                SELECT 1 FROM user_farm_assignments AS assignment
                 WHERE assignment.tenant_id = farm.tenant_id
                   AND assignment.farm_id = farm.id
                   AND assignment.revoked_at IS NULL
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<FarmRecord> mapper;

    PostgresFarmLifecycleStore(
            JdbcTemplate jdbcTemplate,
            RowMapper<FarmRecord> mapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
    }

    Optional<FarmRecord> updateActive(
            ScopeContext scope,
            UUID farmId,
            long expectedVersion,
            boolean active) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        requireVersion(expectedVersion);
        if (!lockFarm(tenantScope, requiredFarmId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE farms AS farm
                   SET active = ?,
                       version = farm.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.version = ?
                   AND farm.active <> ?
                """);
        if (!active) {
            sql.append(" AND NOT (").append(ACTIVE_DEPENDENT_PREDICATE).append(')');
        }
        sql.append(" RETURNING ").append(COLUMNS);
        List<FarmRecord> rows = jdbcTemplate.query(
                sql.toString(),
                mapper,
                active,
                tenantScope.tenantId(),
                requiredFarmId,
                expectedVersion,
                active);
        return exactlyOneOrEmpty(rows);
    }

    boolean hasDeactivationBlockers(ScopeContext scope, UUID farmId) {
        ScopeContext tenantScope = requireTenantScope(scope);
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        String sql = "SELECT (" + ACTIVE_DEPENDENT_PREDICATE + ") AS blocked "
                + "FROM farms AS farm WHERE farm.tenant_id = ? AND farm.id = ?";
        List<Boolean> rows = jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("blocked"),
                tenantScope.tenantId(),
                requiredFarmId);
        return exactlyOneOrEmpty(rows).orElse(false);
    }

    private boolean lockFarm(ScopeContext scope, UUID farmId) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT farm.id
                  FROM farms AS farm
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   FOR UPDATE
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(),
                farmId);
        return exactlyOneOrEmpty(rows).isPresent();
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext requiredScope = Objects.requireNonNull(scope, "scope is required");
        if (requiredScope.type() != ScopeContext.Type.TENANT || requiredScope.resourceId().isPresent()) {
            throw new IllegalArgumentException("Farm lifecycle requires tenant-wide scope");
        }
        return requiredScope;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Farm lifecycle query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
