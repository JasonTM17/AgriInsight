package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.CropCommands;
import com.agriinsight.backend.farm.application.CropRecord;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresCropMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresCropMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<CropRecord> update(
            ScopeContext scope,
            UUID cropId,
            long expectedVersion,
            CropCommands.Update command) {
        ScopeContext tenantScope = requireTenantScope(scope);
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        List<ColumnValue> columns = columns(command);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one crop value must be provided");
        }
        StringBuilder sql = new StringBuilder("UPDATE crops AS crop SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = crop.version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE crop.tenant_id = ?
                   AND crop.id = ?
                   AND crop.version = ?
                   AND (
                """);
        parameters.add(tenantScope.tenantId());
        parameters.add(Objects.requireNonNull(cropId, "cropId is required"));
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(") RETURNING ").append(CropRowMapping.RETURNING_COLUMNS);
        return CropRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), CropRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(CropCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        command.scientificName().ifPresent(value -> columns.add(new ColumnValue(
                "scientific_name", nullable(value.orElse(null)))));
        return columns;
    }

    private void appendAssignments(
            StringBuilder sql,
            List<Object> parameters,
            List<ColumnValue> columns) {
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
            StringBuilder sql,
            List<Object> parameters,
            List<ColumnValue> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            ColumnValue column = columns.get(index);
            sql.append("crop.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }

    private ScopeContext requireTenantScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() != ScopeContext.Type.TENANT || required.resourceId().isPresent()) {
            throw new IllegalArgumentException("Crop mutation requires tenant-wide scope");
        }
        return required;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
