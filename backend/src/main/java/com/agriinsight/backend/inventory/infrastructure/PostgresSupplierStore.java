package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.SupplierCommands;
import com.agriinsight.backend.inventory.application.SupplierPage;
import com.agriinsight.backend.inventory.application.SupplierQuery;
import com.agriinsight.backend.inventory.application.SupplierRecord;
import com.agriinsight.backend.inventory.application.SupplierStore;
import com.agriinsight.backend.inventory.domain.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresSupplierStore implements SupplierStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresSupplierMutationStore mutations;
    private final PostgresSupplierLifecycleStore lifecycle;

    public PostgresSupplierStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresSupplierMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresSupplierLifecycleStore(jdbcTemplate, mutations);
    }

    @Override
    public SupplierPage findAll(ScopeContext scope, SupplierQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE supplier.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(InventoryCatalogScopeSql.require(scope).tenantId());
        InventoryCatalogScopeSql.append(sql, parameters, scope, "supplier");
        query.active().ifPresent(active -> addFilter(sql, parameters, "supplier.active", active));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(supplier.code)) > 0")
                    .append(" OR position(lower(?) in lower(supplier.display_name)) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(supplier.display_name), supplier.code, supplier.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<SupplierRecord> rows = jdbcTemplate.query(
                sql.toString(), SupplierRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<SupplierRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new SupplierPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<SupplierRecord> findById(ScopeContext scope, UUID supplierId) {
        UUID target = Objects.requireNonNull(supplierId, "supplierId is required");
        StringBuilder sql = baseSelect()
                .append(" WHERE supplier.tenant_id = ? AND supplier.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(InventoryCatalogScopeSql.require(scope).tenantId());
        parameters.add(target);
        InventoryCatalogScopeSql.append(sql, parameters, scope, "supplier");
        return SupplierRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), SupplierRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public SupplierRecord create(ScopeContext scope, Supplier supplier) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Supplier");
        Objects.requireNonNull(supplier, "supplier is required");
        if (!tenantScope.tenantId().equals(supplier.tenantId())) {
            throw new IllegalArgumentException("Supplier cannot switch tenants");
        }
        return SupplierRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO suppliers (id, tenant_id, code, display_name)
                VALUES (?, ?, ?, ?)
                RETURNING %s
                """.formatted(SupplierRowMapping.RETURNING_COLUMNS),
                SupplierRowMapping.MAPPER,
                supplier.id(), supplier.tenantId(), supplier.code(), supplier.displayName()))
                .orElseThrow(() -> new IllegalStateException("Supplier was not created"));
    }

    @Override
    public Optional<SupplierRecord> update(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            SupplierCommands.Update command) {
        return mutations.update(scope, supplierId, expectedVersion, command);
    }

    @Override
    public Optional<SupplierRecord> updateActive(
            ScopeContext scope, UUID supplierId, long expectedVersion, boolean active) {
        return lifecycle.updateActive(scope, supplierId, expectedVersion, active);
    }

    @Override
    public boolean hasReferences(ScopeContext scope, UUID supplierId) {
        return mutations.hasReferences(scope, supplierId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(SupplierRowMapping.SELECT_COLUMNS)
                .append(" FROM suppliers AS supplier");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }
}
