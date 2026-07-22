package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityCommands;
import com.agriinsight.backend.operations.application.ActivityRecord;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresActivityMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresActivityMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<ActivityRecord> update(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityCommands.Update command) {
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        List<ColumnValue> columns = columns(command);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one activity field must be provided");
        }
        ScopeContext writeScope = requireTargetedWriteScope(scope);
        UUID farmId = writeScope.resourceId().orElse(null);
        if (farmId != null && !ActivityScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, farmId)) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("UPDATE activities AS activity SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = activity.version + 1, updated_at = CURRENT_TIMESTAMP
                  FROM farms AS farm
                 WHERE farm.tenant_id = activity.tenant_id
                   AND farm.id = activity.farm_id
                   AND activity.tenant_id = ?
                   AND activity.id = ?
                   AND activity.version = ?
                   AND activity.status IN ('PLANNED', 'STARTED')
                   AND (
                """);
        parameters.add(writeScope.tenantId());
        parameters.add(Objects.requireNonNull(activityId, "activityId is required"));
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(')');
        appendWriteTarget(sql, parameters, writeScope);
        sql.append(" RETURNING ").append(ActivityRowMapping.SELECT_COLUMNS);
        return ActivityRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), ActivityRowMapping.MAPPER, parameters.toArray()));
    }

    Optional<ActivityRecord> transition(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityStatus sourceStatus,
            ActivityStatus targetStatus,
            Instant effectiveAt) {
        requireVersion(expectedVersion);
        ActivityStatus source = Objects.requireNonNull(sourceStatus, "sourceStatus is required");
        ActivityStatus target = Objects.requireNonNull(targetStatus, "targetStatus is required");
        if (!source.canTransitionTo(target)) {
            throw new IllegalArgumentException("Activity transition is not allowed");
        }
        ScopeContext writeScope = requireTargetedWriteScope(scope);
        UUID farmId = writeScope.resourceId().orElse(null);
        if (farmId != null && !ActivityScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, farmId)) {
            return Optional.empty();
        }
        String timeAssignment = switch (target) {
            case STARTED -> "started_at = ?, completed_at = NULL, cancelled_at = NULL";
            case COMPLETED -> "completed_at = ?, cancelled_at = NULL";
            case CANCELLED -> "cancelled_at = ?, completed_at = NULL";
            case PLANNED -> throw new IllegalArgumentException("Activity cannot transition to PLANNED");
        };
        StringBuilder sql = new StringBuilder("UPDATE activities AS activity SET status = ?, ")
                .append(timeAssignment)
                .append("""
                        , version = activity.version + 1, updated_at = CURRENT_TIMESTAMP
                          FROM farms AS farm
                         WHERE farm.tenant_id = activity.tenant_id
                           AND farm.id = activity.farm_id
                           AND activity.tenant_id = ?
                           AND activity.id = ?
                           AND activity.version = ?
                           AND activity.status = ?
                        """);
        List<Object> parameters = new ArrayList<>(List.of(
                target.name(),
                Timestamp.from(Objects.requireNonNull(effectiveAt, "effectiveAt is required")),
                writeScope.tenantId(),
                Objects.requireNonNull(activityId, "activityId is required"),
                expectedVersion,
                source.name()));
        appendWriteTarget(sql, parameters, writeScope);
        sql.append(" RETURNING ").append(ActivityRowMapping.SELECT_COLUMNS);
        return ActivityRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), ActivityRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(ActivityCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.activityType().ifPresent(value ->
                columns.add(new ColumnValue("activity_type_code", value.name())));
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.title().ifPresent(value -> columns.add(new ColumnValue("title", value)));
        command.description().ifPresent(value -> columns.add(new ColumnValue(
                "description", nullable(value.orElse(null)))));
        command.plannedStartAt().ifPresent(value -> columns.add(new ColumnValue(
                "planned_start_at", Timestamp.from(value))));
        command.dueAt().ifPresent(value -> columns.add(new ColumnValue(
                "due_at", Timestamp.from(value))));
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
            sql.append("activity.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private void appendWriteTarget(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope) {
        scope.resourceId().ifPresent(farmId -> {
            sql.append(" AND farm.id = ?");
            parameters.add(farmId);
        });
    }

    private ScopeContext requireTargetedWriteScope(ScopeContext scope) {
        ScopeContext required = Objects.requireNonNull(scope, "scope is required");
        if (required.type() == ScopeContext.Type.TENANT && required.resourceId().isEmpty()) {
            return required;
        }
        if (required.type() != ScopeContext.Type.FARM || required.resourceId().isEmpty()) {
            throw new IllegalArgumentException("Activity mutation requires tenant-wide or target farm scope");
        }
        return required;
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
