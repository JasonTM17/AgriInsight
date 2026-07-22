package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
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

final class PostgresFieldCreateStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresFieldCreateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    FieldRecord create(ScopeContext scope, Field field) {
        Objects.requireNonNull(field, "field is required");
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope, field.farmId());
        if (!writeScope.tenantId().equals(field.tenantId())) {
            throw new IllegalArgumentException("Field cannot switch tenants");
        }
        Field.Coordinates coordinates = field.coordinates().orElse(null);
        StringBuilder sql = new StringBuilder("""
                INSERT INTO fields (
                    id, tenant_id, farm_id, code, display_name, area_hectares,
                    responsible_employee_id, latitude, longitude, soil_type, irrigation_type)
                SELECT ?, farm.tenant_id, farm.id, ?, ?, ?, ?, ?, ?, ?, ?
                  FROM farms AS farm
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.active
                """);
        List<Object> parameters = parameters(field, coordinates);
        appendResponsibleEmployeeGuard(sql, parameters, field.responsibleEmployeeId());
        FarmScopeSql.append(sql, parameters, writeScope, field.farmId());
        sql.append(" RETURNING ").append(FieldRowMapping.RETURNING_COLUMNS);
        return FieldRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), FieldRowMapping.MAPPER, parameters.toArray()))
                .orElseThrow(() -> new IllegalStateException("Field parents are not available"));
    }

    private List<Object> parameters(Field field, Field.Coordinates coordinates) {
        return new ArrayList<>(List.of(
                field.id(), field.code(), field.displayName(), field.areaHectares(),
                nullable(field.responsibleEmployeeId().orElse(null), Types.OTHER),
                nullable(coordinates == null ? null : coordinates.latitude(), Types.NUMERIC),
                nullable(coordinates == null ? null : coordinates.longitude(), Types.NUMERIC),
                nullable(field.soilType().orElse(null), Types.VARCHAR),
                nullable(field.irrigationType().orElse(null), Types.VARCHAR),
                field.tenantId(), field.farmId()));
    }

    private void appendResponsibleEmployeeGuard(
            StringBuilder sql,
            List<Object> parameters,
            Optional<UUID> employeeId) {
        employeeId.ifPresent(id -> {
            sql.append("""
                     AND EXISTS (
                           SELECT 1 FROM employees AS employee
                            WHERE employee.tenant_id = farm.tenant_id
                              AND employee.id = ? AND employee.active)
                    """);
            parameters.add(id);
        });
    }

    private Object nullable(Object value, int type) {
        return value == null ? new SqlParameterValue(type, null) : value;
    }
}
