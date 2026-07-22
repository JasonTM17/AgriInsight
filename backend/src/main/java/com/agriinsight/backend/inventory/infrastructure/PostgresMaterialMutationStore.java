package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.MaterialCommands;
import com.agriinsight.backend.inventory.application.MaterialRecord;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresMaterialMutationStore {

    private static final String REFERENCE_PREDICATE = """
            EXISTS (SELECT 1 FROM inventory_transactions AS tx
                     WHERE tx.tenant_id = material.tenant_id
                       AND tx.material_id = material.id)
            OR EXISTS (SELECT 1 FROM stock_lots AS lot
                       WHERE lot.tenant_id = material.tenant_id
                         AND lot.material_id = material.id)
            OR EXISTS (SELECT 1 FROM stock_balances AS balance
                       WHERE balance.tenant_id = material.tenant_id
                         AND balance.material_id = material.id)
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresMaterialMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<MaterialRecord> update(
            ScopeContext scope,
            UUID materialId,
            long expectedVersion,
            MaterialCommands.Update command) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Material");
        UUID target = Objects.requireNonNull(materialId, "materialId is required");
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        if (!lockMaterial(tenantScope, target)) {
            return Optional.empty();
        }
        List<ColumnValue> columns = columns(command);
        StringBuilder sql = new StringBuilder("UPDATE materials AS material SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = material.version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE material.tenant_id = ? AND material.id = ? AND material.version = ?
                   AND (
                """);
        parameters.add(tenantScope.tenantId());
        parameters.add(target);
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(')');
        if (command.baseUnit().isPresent()) {
            sql.append(" AND (material.base_unit = ? OR NOT (")
                    .append(REFERENCE_PREDICATE)
                    .append("))");
            parameters.add(command.baseUnit().orElseThrow().name());
        }
        sql.append(" RETURNING ").append(MaterialRowMapping.RETURNING_COLUMNS);
        return MaterialRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), MaterialRowMapping.MAPPER, parameters.toArray()));
    }

    boolean hasReferences(ScopeContext scope, UUID materialId) {
        ScopeContext tenantScope = InventoryCatalogScopeSql.requireTenantWrite(scope, "Material");
        String sql = "SELECT (" + REFERENCE_PREDICATE + ") AS referenced "
                + "FROM materials AS material WHERE material.tenant_id = ? AND material.id = ?";
        List<Boolean> rows = jdbcTemplate.query(
                sql,
                (result, rowNumber) -> result.getBoolean("referenced"),
                tenantScope.tenantId(),
                Objects.requireNonNull(materialId, "materialId is required"));
        return MaterialRowMapping.exactlyOneOrEmpty(rows).orElse(false);
    }

    private List<ColumnValue> columns(MaterialCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        command.baseUnit().ifPresent(value -> columns.add(new ColumnValue("base_unit", value.name())));
        command.minimumStockQuantity().ifPresent(value -> columns.add(new ColumnValue(
                "minimum_stock_quantity", nullableDecimal(value.orElse(null)))));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one material field must be provided");
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
            sql.append("material.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
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

    private Object nullableDecimal(java.math.BigDecimal value) {
        return value == null ? new SqlParameterValue(Types.NUMERIC, null) : value;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
