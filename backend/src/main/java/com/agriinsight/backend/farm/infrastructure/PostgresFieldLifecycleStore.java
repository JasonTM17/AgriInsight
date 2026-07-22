package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FieldRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresFieldLifecycleStore {

    private static final String LIVE_DEPENDENT_PREDICATE = """
            EXISTS (
                SELECT 1 FROM seasons AS season
                 WHERE season.tenant_id = field.tenant_id
                   AND season.farm_id = field.farm_id
                   AND season.field_id = field.id
                   AND season.status IN ('PLANNED', 'ACTIVE')
            ) OR EXISTS (
                SELECT 1 FROM activities AS activity
                 WHERE activity.tenant_id = field.tenant_id
                   AND activity.farm_id = field.farm_id
                   AND activity.field_id = field.id
                   AND activity.status IN ('PLANNED', 'STARTED')
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    PostgresFieldLifecycleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<FieldRecord> updateActive(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            boolean active) {
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope);
        UUID requiredFieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        requireVersion(expectedVersion);
        if (!lockField(writeScope, requiredFieldId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                UPDATE fields AS field
                   SET active = ?, version = field.version + 1,
                       updated_at = CURRENT_TIMESTAMP
                  FROM farms AS farm
                 WHERE farm.tenant_id = field.tenant_id
                   AND farm.id = field.farm_id
                   AND field.tenant_id = ?
                   AND field.id = ?
                   AND field.version = ?
                   AND field.active <> ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                active, writeScope.tenantId(), requiredFieldId, expectedVersion, active));
        if (active) {
            sql.append("""
                     AND farm.active
                     AND (field.responsible_employee_id IS NULL OR EXISTS (
                           SELECT 1 FROM employees AS employee
                            WHERE employee.tenant_id = field.tenant_id
                              AND employee.id = field.responsible_employee_id
                              AND employee.active))
                    """);
        } else {
            sql.append(" AND NOT (").append(LIVE_DEPENDENT_PREDICATE).append(')');
        }
        FarmScopeSql.append(sql, parameters, writeScope, writeScope.resourceId().orElse(null));
        sql.append(" RETURNING ").append(FieldRowMapping.SELECT_COLUMNS);
        return FieldRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), FieldRowMapping.MAPPER, parameters.toArray()));
    }

    boolean hasDeactivationBlockers(ScopeContext scope, UUID fieldId) {
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope);
        UUID requiredFieldId = Objects.requireNonNull(fieldId, "fieldId is required");
        StringBuilder sql = new StringBuilder("SELECT (")
                .append(LIVE_DEPENDENT_PREDICATE)
                .append("""
                        ) AS blocked
                          FROM fields AS field
                          JOIN farms AS farm
                            ON farm.tenant_id = field.tenant_id AND farm.id = field.farm_id
                         WHERE field.tenant_id = ? AND field.id = ?
                        """);
        List<Object> parameters = new ArrayList<>(List.of(writeScope.tenantId(), requiredFieldId));
        FarmScopeSql.append(sql, parameters, writeScope, writeScope.resourceId().orElse(null));
        return FieldRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql.toString(),
                (result, rowNumber) -> result.getBoolean("blocked"),
                parameters.toArray())).orElse(false);
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

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
