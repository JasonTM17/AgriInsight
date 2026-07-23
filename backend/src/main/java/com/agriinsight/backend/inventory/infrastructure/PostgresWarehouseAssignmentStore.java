package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentRecord;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentPage;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentQuery;
import com.agriinsight.backend.inventory.application.WarehouseAssignmentStore;
import com.agriinsight.backend.inventory.domain.WarehouseAssignment;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresWarehouseAssignmentStore implements WarehouseAssignmentStore {

    private static final String COLUMNS = """
            id, tenant_id, user_profile_id, warehouse_id, revoked_at, version
            """;
    private static final RowMapper<WarehouseAssignmentRecord> MAPPER = (result, rowNumber) -> {
        Timestamp revokedAt = result.getTimestamp("revoked_at");
        return new WarehouseAssignmentRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("user_profile_id", UUID.class),
                result.getObject("warehouse_id", UUID.class),
                revokedAt == null ? Optional.empty() : Optional.of(revokedAt.toInstant()),
                result.getLong("version"));
    };

    private final JdbcTemplate jdbcTemplate;

    public PostgresWarehouseAssignmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public WarehouseAssignmentPage findAll(
            ScopeContext scope, WarehouseAssignmentQuery query) {
        ScopeContext tenantScope = tenantScope(scope);
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM user_warehouse_assignments WHERE tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantScope.tenantId());
        query.userProfileId().ifPresent(value -> addFilter(
                sql, parameters, "user_profile_id", value));
        query.warehouseId().ifPresent(value -> addFilter(
                sql, parameters, "warehouse_id", value));
        query.active().ifPresent(value -> sql.append(
                value ? " AND revoked_at IS NULL" : " AND revoked_at IS NOT NULL"));
        sql.append(" ORDER BY user_profile_id, warehouse_id, id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<WarehouseAssignmentRecord> rows = jdbcTemplate.query(
                sql.toString(), MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<WarehouseAssignmentRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new WarehouseAssignmentPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<WarehouseAssignmentRecord> findById(
            ScopeContext scope,
            UUID assignmentId) {
        ScopeContext tenantScope = tenantScope(scope);
        UUID target = Objects.requireNonNull(assignmentId, "assignmentId is required");
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS
                        + " FROM user_warehouse_assignments WHERE tenant_id = ? AND id = ?",
                MAPPER,
                tenantScope.tenantId(),
                target));
    }

    @Override
    public Optional<WarehouseAssignmentRecord> findActive(
            ScopeContext scope,
            UUID userProfileId,
            UUID warehouseId) {
        ScopeContext tenantScope = tenantScope(scope);
        return exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + COLUMNS + """
                         FROM user_warehouse_assignments
                        WHERE tenant_id = ? AND user_profile_id = ? AND warehouse_id = ?
                          AND revoked_at IS NULL
                        """,
                MAPPER,
                tenantScope.tenantId(),
                Objects.requireNonNull(userProfileId, "userProfileId is required"),
                Objects.requireNonNull(warehouseId, "warehouseId is required")));
    }

    @Override
    public boolean activeProfileExists(ScopeContext scope, UUID userProfileId) {
        ScopeContext tenantScope = tenantScope(scope);
        return count("""
                SELECT count(*) FROM user_profiles
                 WHERE tenant_id = ? AND id = ? AND active
                """, tenantScope.tenantId(),
                Objects.requireNonNull(userProfileId, "userProfileId is required")) == 1;
    }

    @Override
    public boolean activeWarehouseExists(ScopeContext scope, UUID warehouseId) {
        ScopeContext tenantScope = tenantScope(scope);
        return count("""
                SELECT count(*) FROM warehouses
                 WHERE tenant_id = ? AND id = ? AND active
                """, tenantScope.tenantId(),
                Objects.requireNonNull(warehouseId, "warehouseId is required")) == 1;
    }

    @Override
    public Optional<WarehouseAssignmentRecord> create(
            ScopeContext scope,
            WarehouseAssignment assignment) {
        ScopeContext tenantScope = tenantScope(scope);
        Objects.requireNonNull(assignment, "assignment is required");
        if (!tenantScope.tenantId().equals(assignment.tenantId())) {
            throw new IllegalArgumentException("Warehouse assignment cannot switch tenants");
        }
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO user_warehouse_assignments (
                    id, tenant_id, user_profile_id, warehouse_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, user_profile_id, warehouse_id)
                    WHERE revoked_at IS NULL
                DO NOTHING
                RETURNING id, tenant_id, user_profile_id, warehouse_id, revoked_at, version
                """,
                MAPPER,
                assignment.id(),
                assignment.tenantId(),
                assignment.userProfileId(),
                assignment.warehouseId()));
    }

    @Override
    public Optional<WarehouseAssignmentRecord> revoke(
            ScopeContext scope,
            UUID assignmentId,
            long expectedVersion) {
        ScopeContext tenantScope = tenantScope(scope);
        UUID target = Objects.requireNonNull(assignmentId, "assignmentId is required");
        requireVersion(expectedVersion);
        return exactlyOneOrEmpty(jdbcTemplate.query("""
                UPDATE user_warehouse_assignments
                   SET revoked_at = CURRENT_TIMESTAMP,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND id = ? AND version = ? AND revoked_at IS NULL
                RETURNING id, tenant_id, user_profile_id, warehouse_id, revoked_at, version
                """,
                MAPPER,
                tenantScope.tenantId(),
                target,
                expectedVersion));
    }

    private long count(String sql, Object... parameters) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return result == null ? 0 : result;
    }

    private void addFilter(
            StringBuilder sql,
            List<Object> parameters,
            String column,
            Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private ScopeContext tenantScope(ScopeContext scope) {
        return InventoryCatalogScopeSql.requireTenantWrite(scope, "Warehouse assignment");
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private <T> Optional<T> exactlyOneOrEmpty(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Warehouse assignment query returned more than one row");
        }
        return rows.stream().findFirst();
    }
}
