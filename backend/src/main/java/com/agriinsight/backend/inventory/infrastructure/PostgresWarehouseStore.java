package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehousePage;
import com.agriinsight.backend.inventory.application.WarehouseQuery;
import com.agriinsight.backend.inventory.application.WarehouseRecord;
import com.agriinsight.backend.inventory.application.WarehouseStore;
import com.agriinsight.backend.inventory.domain.Warehouse;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresWarehouseStore implements WarehouseStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresWarehouseMutationStore mutations;
    private final PostgresWarehouseLifecycleStore lifecycle;

    public PostgresWarehouseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresWarehouseMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresWarehouseLifecycleStore(jdbcTemplate);
    }

    @Override
    public WarehousePage findAll(ScopeContext scope, WarehouseQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE warehouse.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        WarehouseScopeSql.append(sql, parameters, scope, null);
        query.active().ifPresent(active -> addFilter(sql, parameters, "warehouse.active", active));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(warehouse.code)) > 0")
                    .append(" OR position(lower(?) in lower(warehouse.display_name)) > 0")
                    .append(" OR position(lower(?) in lower(COALESCE(warehouse.location_text, ''))) > 0)");
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(warehouse.display_name), warehouse.code, warehouse.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<WarehouseRecord> rows = jdbcTemplate.query(
                sql.toString(), WarehouseRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<WarehouseRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new WarehousePage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<WarehouseRecord> findById(ScopeContext scope, UUID warehouseId) {
        UUID target = Objects.requireNonNull(warehouseId, "warehouseId is required");
        StringBuilder sql = baseSelect()
                .append(" WHERE warehouse.tenant_id = ? AND warehouse.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        parameters.add(target);
        WarehouseScopeSql.append(sql, parameters, scope, target);
        return WarehouseRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), WarehouseRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public WarehouseRecord create(ScopeContext scope, Warehouse warehouse) {
        ScopeContext tenantScope = requireTenantScope(scope);
        Objects.requireNonNull(warehouse, "warehouse is required");
        if (!tenantScope.tenantId().equals(warehouse.tenantId())) {
            throw new IllegalArgumentException("Warehouse cannot switch tenants");
        }
        return WarehouseRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO warehouses (id, tenant_id, code, display_name, location_text)
                VALUES (?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(WarehouseRowMapping.RETURNING_COLUMNS),
                WarehouseRowMapping.MAPPER,
                warehouse.id(), warehouse.tenantId(), warehouse.code(), warehouse.displayName(),
                nullable(warehouse.locationText().orElse(null))))
                .orElseThrow(() -> new IllegalStateException("Warehouse was not created"));
    }

    @Override
    public Optional<WarehouseRecord> update(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> locationText) {
        return mutations.update(
                scope, warehouseId, expectedVersion, code, displayName, locationText);
    }

    @Override
    public Optional<WarehouseRecord> updateActive(
            ScopeContext scope, UUID warehouseId, long expectedVersion, boolean active) {
        return lifecycle.updateActive(scope, warehouseId, expectedVersion, active);
    }

    @Override
    public boolean hasDeactivationBlockers(ScopeContext scope, UUID warehouseId) {
        return lifecycle.hasDeactivationBlockers(scope, warehouseId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(WarehouseRowMapping.SELECT_COLUMNS)
                .append(" FROM warehouses AS warehouse");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = requireScope(scope);
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Warehouse creation requires tenant-wide scope");
        }
        return required;
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }
}
