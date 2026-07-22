package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FieldCommands;
import com.agriinsight.backend.farm.application.FieldRecord;
import com.agriinsight.backend.farm.domain.Field;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresFieldMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresFieldMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<FieldRecord> update(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            FieldCommands.Update command) {
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        List<ColumnValue> columns = columns(command);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one field value must be provided");
        }
        UUID requiredFieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope);
        if (!lockField(writeScope, requiredFieldId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("UPDATE fields AS field SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = field.version + 1, updated_at = CURRENT_TIMESTAMP
                  FROM farms AS farm
                 WHERE farm.tenant_id = field.tenant_id
                   AND farm.id = field.farm_id
                   AND field.tenant_id = ?
                   AND field.id = ?
                   AND field.version = ?
                   AND (
                """);
        parameters.add(writeScope.tenantId());
        parameters.add(requiredFieldId);
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(')');
        FarmScopeSql.append(sql, parameters, writeScope, writeScope.resourceId().orElse(null));
        sql.append(" RETURNING ").append(FieldRowMapping.SELECT_COLUMNS);
        return FieldRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), FieldRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(FieldCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        command.areaHectares().ifPresent(value -> columns.add(new ColumnValue("area_hectares", value)));
        command.responsibleEmployeeId().ifPresent(value -> columns.add(new ColumnValue(
                "responsible_employee_id", nullable(value.orElse(null), Types.OTHER))));
        command.coordinates().ifPresent(value -> addCoordinates(columns, value));
        command.soilType().ifPresent(value -> columns.add(new ColumnValue(
                "soil_type", nullable(value.orElse(null), Types.VARCHAR))));
        command.irrigationType().ifPresent(value -> columns.add(new ColumnValue(
                "irrigation_type", nullable(value.orElse(null), Types.VARCHAR))));
        return columns;
    }

    private void addCoordinates(
            List<ColumnValue> columns,
            Optional<Field.Coordinates> coordinates) {
        Field.Coordinates value = coordinates.orElse(null);
        columns.add(new ColumnValue(
                "latitude", nullable(value == null ? null : value.latitude(), Types.NUMERIC)));
        columns.add(new ColumnValue(
                "longitude", nullable(value == null ? null : value.longitude(), Types.NUMERIC)));
    }

    private boolean lockField(ScopeContext scope, UUID fieldId) {
        StringBuilder sql = new StringBuilder("""
                SELECT field.id
                  FROM fields AS field
                  JOIN farms AS farm
                    ON farm.tenant_id = field.tenant_id AND farm.id = field.farm_id
                 WHERE field.tenant_id = ? AND field.id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(scope.tenantId(), fieldId));
        FarmScopeSql.append(sql, parameters, scope, scope.resourceId().orElse(null));
        sql.append(" FOR UPDATE OF field");
        return FieldRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(),
                (result, rowNumber) -> result.getObject("id", UUID.class),
                parameters.toArray())).isPresent();
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
            sql.append("field.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private Object nullable(Object value, int type) {
        return value == null ? new SqlParameterValue(type, null) : value;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
