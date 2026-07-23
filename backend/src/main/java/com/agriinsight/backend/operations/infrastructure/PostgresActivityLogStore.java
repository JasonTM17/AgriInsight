package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityLogAccess;
import com.agriinsight.backend.operations.application.ActivityLogRecord;
import com.agriinsight.backend.operations.application.ActivityLogStore;
import com.agriinsight.backend.operations.domain.ActivityLog;
import java.sql.Timestamp;
import java.sql.Types;
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
public class PostgresActivityLogStore implements ActivityLogStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresActivityLogAccessResolver accessResolver;

    public PostgresActivityLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.accessResolver = new PostgresActivityLogAccessResolver(jdbcTemplate);
    }

    @Override
    public Optional<ActivityLogAccess> resolveAccess(ScopeContext scope, UUID activityId) {
        return accessResolver.resolve(scope, activityId);
    }

    @Override
    public Optional<ActivityLogRecord> findById(
            ScopeContext scope,
            UUID activityId,
            UUID logId) {
        ScopeContext required = ActivityLogScope.require(scope, activityId);
        return ActivityLogRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                "SELECT " + ActivityLogRowMapping.COLUMNS
                        + " FROM activity_logs WHERE tenant_id = ? AND activity_id = ? AND id = ?",
                ActivityLogRowMapping.MAPPER,
                required.tenantId(), activityId,
                Objects.requireNonNull(logId, "logId is required")));
    }

    @Override
    public Optional<ActivityLogRecord> append(ScopeContext scope, ActivityLog log) {
        ActivityLog value = Objects.requireNonNull(log, "log is required");
        ScopeContext required = ActivityLogScope.require(scope, value.activityId());
        if (!required.tenantId().equals(value.tenantId())
                || !required.profileId().equals(value.authorProfileId())) {
            throw new IllegalArgumentException("Activity log cannot switch tenant or author");
        }
        Optional<ActivityLogAccess> access = resolveAccess(required, value.activityId());
        if (access.isEmpty() || !workerMayAppend(required, access.orElseThrow(), value)) {
            return Optional.empty();
        }
        return value.correctsLogId().isPresent()
                ? appendCorrection(required, value)
                : appendOriginal(required, value);
    }

    private boolean workerMayAppend(
            ScopeContext scope,
            ActivityLogAccess access,
            ActivityLog log) {
        if (access.manager()) {
            return true;
        }
        if (!access.workerEmployeeId().filter(log.employeeId()::equals).isPresent()) {
            return false;
        }
        if (log.correctsLogId().isEmpty()) {
            return true;
        }
        return findById(scope, log.activityId(), log.correctsLogId().orElseThrow())
                .filter(original -> original.authorProfileId().equals(scope.profileId()))
                .filter(original -> original.employeeId().equals(log.employeeId()))
                .isPresent();
    }

    private Optional<ActivityLogRecord> appendOriginal(
            ScopeContext scope,
            ActivityLog log) {
        return ActivityLogRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO activity_logs (
                    id, tenant_id, activity_id, employee_id, author_profile_id,
                    occurred_at, notes, quantity, unit_code, evidence_uri)
                SELECT ?, activity.tenant_id, activity.id, employee.id, ?, ?, ?, ?, ?, ?
                  FROM activities AS activity
                  JOIN employees AS employee
                    ON employee.tenant_id = activity.tenant_id
                   AND employee.id = ?
                   AND employee.active
                  JOIN activity_assignees AS assignment
                    ON assignment.tenant_id = activity.tenant_id
                   AND assignment.activity_id = activity.id
                   AND assignment.employee_id = employee.id
                   AND assignment.revoked_at IS NULL
                 WHERE activity.tenant_id = ? AND activity.id = ?
                RETURNING %s
                """.formatted(ActivityLogRowMapping.COLUMNS), ActivityLogRowMapping.MAPPER,
                log.id(), log.authorProfileId(), Timestamp.from(log.occurredAt()),
                nullable(log.notes().orElse(null), Types.VARCHAR),
                nullable(log.quantity().orElse(null), Types.NUMERIC),
                nullable(log.unit().map(Enum::name).orElse(null), Types.VARCHAR),
                nullable(log.evidenceUri().orElse(null), Types.VARCHAR),
                log.employeeId(), scope.tenantId(), log.activityId()));
    }

    private Optional<ActivityLogRecord> appendCorrection(
            ScopeContext scope,
            ActivityLog log) {
        return ActivityLogRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO activity_logs (
                    id, tenant_id, activity_id, employee_id, author_profile_id,
                    occurred_at, notes, quantity, unit_code, evidence_uri,
                    corrects_log_id, correction_kind, correction_reason)
                SELECT ?, original.tenant_id, original.activity_id, original.employee_id,
                       ?, ?, ?, ?, ?, ?, original.id, ?, ?
                  FROM activity_logs AS original
                 WHERE original.tenant_id = ?
                   AND original.activity_id = ?
                   AND original.id = ?
                   AND NOT EXISTS (
                       SELECT 1 FROM activity_logs AS successor
                        WHERE successor.tenant_id = original.tenant_id
                          AND successor.corrects_log_id = original.id)
                RETURNING %s
                """.formatted(ActivityLogRowMapping.COLUMNS), ActivityLogRowMapping.MAPPER,
                log.id(), log.authorProfileId(), Timestamp.from(log.occurredAt()),
                nullable(log.notes().orElse(null), Types.VARCHAR),
                nullable(log.quantity().orElse(null), Types.NUMERIC),
                nullable(log.unit().map(Enum::name).orElse(null), Types.VARCHAR),
                nullable(log.evidenceUri().orElse(null), Types.VARCHAR),
                log.correctionKind().orElseThrow().name(), log.correctionReason().orElseThrow(),
                scope.tenantId(), log.activityId(), log.correctsLogId().orElseThrow()));
    }

    private Object nullable(Object value, int type) {
        return value == null ? new SqlParameterValue(type, null) : value;
    }

}
