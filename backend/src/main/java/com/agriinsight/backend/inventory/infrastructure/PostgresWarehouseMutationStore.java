package com.agriinsight.backend.inventory.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.application.WarehouseRecord;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresWarehouseMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresWarehouseMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<WarehouseRecord> update(
            ScopeContext scope,
            UUID warehouseId,
            long expectedVersion,
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> locationText) {
        requireVersion(expectedVersion);
        ScopeContext writeScope = WarehouseScopeSql.requireWriteScope(scope, warehouseId);
        if (!WarehouseScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, warehouseId)) {
            return Optional.empty();
        }
        List<ColumnValue> columns = columns(code, displayName, locationText);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one warehouse field must be provided");
        }
        StringBuilder sql = new StringBuilder("UPDATE warehouses AS warehouse SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = warehouse.version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE warehouse.tenant_id = ?
                   AND warehouse.id = ?
                   AND warehouse.version = ?
                   AND (
                """);
        parameters.add(writeScope.tenantId());
        parameters.add(Objects.requireNonNull(warehouseId, "warehouseId is required"));
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(')');
        WarehouseScopeSql.append(sql, parameters, writeScope, warehouseId);
        sql.append(" RETURNING ").append(WarehouseRowMapping.RETURNING_COLUMNS);
        return WarehouseRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(), WarehouseRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> locationText) {
        List<ColumnValue> columns = new ArrayList<>();
        Objects.requireNonNull(code, "code is required")
                .ifPresent(value -> columns.add(new ColumnValue("code", value)));
        Objects.requireNonNull(displayName, "displayName is required")
                .ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        Objects.requireNonNull(locationText, "locationText is required")
                .ifPresent(value -> columns.add(new ColumnValue(
                        "location_text", nullable(value.orElse(null)))));
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
            sql.append("warehouse.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
