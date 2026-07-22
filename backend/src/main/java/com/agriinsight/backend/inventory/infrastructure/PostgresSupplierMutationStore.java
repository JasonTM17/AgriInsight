package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.SupplierCommands;
import com.agriinsight.backend.inventory.application.SupplierRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresSupplierMutationStore {

    private static final String REFERENCE_PREDICATE = """
            EXISTS (SELECT 1 FROM inventory_transactions AS tx
                     WHERE tx.tenant_id = supplier.tenant_id
                       AND tx.supplier_id = supplier.id)
            OR EXISTS (SELECT 1 FROM stock_lots AS lot
                       WHERE lot.tenant_id = supplier.tenant_id
                         AND lot.supplier_id = supplier.id)
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresSupplierMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<SupplierRecord> update(
            ScopeContext scope,
            UUID supplierId,
            long expectedVersion,
            SupplierCommands.Update command) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Supplier");
        UUID target = Objects.requireNonNull(supplierId, "supplierId is required");
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        if (!lockSupplier(tenantScope, target)) {
            return Optional.empty();
        }
        List<ColumnValue> columns = columns(command);
        StringBuilder sql = new StringBuilder("UPDATE suppliers AS supplier SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = supplier.version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE supplier.tenant_id = ? AND supplier.id = ? AND supplier.version = ?
                   AND (
                """);
        parameters.add(tenantScope.tenantId());
        parameters.add(target);
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(") RETURNING ").append(SupplierRowMapping.RETURNING_COLUMNS);
        return SupplierRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), SupplierRowMapping.MAPPER, parameters.toArray()));
    }

    boolean hasReferences(ScopeContext scope, UUID supplierId) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Supplier");
        String sql = "SELECT (" + REFERENCE_PREDICATE + ") AS referenced "
                + "FROM suppliers AS supplier WHERE supplier.tenant_id = ? AND supplier.id = ?";
        List<Boolean> rows = jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("referenced"),
                tenantScope.tenantId(),
                Objects.requireNonNull(supplierId, "supplierId is required"));
        return SupplierRowMapping.exactlyOneOrEmpty(rows).orElse(false);
    }

    private List<ColumnValue> columns(SupplierCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one supplier field must be provided");
        }
        return columns;
    }

    private void appendAssignments(
            StringBuilder sql, List<Object> parameters, List<ColumnValue> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            ColumnValue column = columns.get(index);
            sql.append(column.name()).append(" = ?");
            parameters.add(column.value());
        }
    }

    private void appendDifferencePredicate(
            StringBuilder sql, List<Object> parameters, List<ColumnValue> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            ColumnValue column = columns.get(index);
            sql.append("supplier.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
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

    private record ColumnValue(String name, Object value) {
    }
}
