package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.MaterialCommands;
import com.agriinsight.backend.inventory.application.MaterialPage;
import com.agriinsight.backend.inventory.application.MaterialQuery;
import com.agriinsight.backend.inventory.application.MaterialRecord;
import com.agriinsight.backend.inventory.application.MaterialStore;
import com.agriinsight.backend.inventory.domain.Material;
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
public class PostgresMaterialStore implements MaterialStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresMaterialMutationStore mutations;
    private final PostgresMaterialLifecycleStore lifecycle;

    public PostgresMaterialStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresMaterialMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresMaterialLifecycleStore(jdbcTemplate, mutations);
    }

    @Override
    public MaterialPage findAll(ScopeContext scope, MaterialQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE material.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(InventoryCatalogScopeSql.require(scope).tenantId());
        InventoryCatalogScopeSql.append(sql, parameters, scope, "material");
        query.active().ifPresent(active -> addFilter(sql, parameters, "material.active", active));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(material.code)) > 0")
                    .append(" OR position(lower(?) in lower(material.display_name)) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(material.display_name), material.code, material.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<MaterialRecord> rows = jdbcTemplate.query(
                sql.toString(), MaterialRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<MaterialRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new MaterialPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<MaterialRecord> findById(ScopeContext scope, UUID materialId) {
        UUID target = Objects.requireNonNull(materialId, "materialId is required");
        StringBuilder sql = baseSelect()
                .append(" WHERE material.tenant_id = ? AND material.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(InventoryCatalogScopeSql.require(scope).tenantId());
        parameters.add(target);
        InventoryCatalogScopeSql.append(sql, parameters, scope, "material");
        return MaterialRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), MaterialRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public MaterialRecord create(ScopeContext scope, Material material) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Material");
        Objects.requireNonNull(material, "material is required");
        if (!tenantScope.tenantId().equals(material.tenantId())) {
            throw new IllegalArgumentException("Material cannot switch tenants");
        }
        return MaterialRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO materials (
                    id, tenant_id, code, display_name, base_unit, minimum_stock_quantity)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(MaterialRowMapping.RETURNING_COLUMNS),
                MaterialRowMapping.MAPPER,
                material.id(), material.tenantId(), material.code(), material.displayName(),
                material.baseUnit().name(), nullableDecimal(
                        material.minimumStockQuantity().orElse(null))))
                .orElseThrow(() -> new IllegalStateException("Material was not created"));
    }

    @Override
    public Optional<MaterialRecord> update(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            MaterialCommands.Update command) {
        return mutations.update(scope, materialId, expectedVersion, command);
    }

    @Override
    public Optional<MaterialRecord> updateActive(
            ScopeContext scope, UUID materialId, long expectedVersion, boolean active) {
        return lifecycle.updateActive(scope, materialId, expectedVersion, active);
    }

    @Override
    public boolean hasReferences(ScopeContext scope, UUID materialId) {
        return mutations.hasReferences(scope, materialId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(MaterialRowMapping.SELECT_COLUMNS)
                .append(" FROM materials AS material");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private Object nullableDecimal(java.math.BigDecimal value) {
        return value == null ? new SqlParameterValue(Types.NUMERIC, null) : value;
    }
}
