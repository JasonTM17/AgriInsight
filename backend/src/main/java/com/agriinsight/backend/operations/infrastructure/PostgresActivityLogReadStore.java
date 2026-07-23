package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityLogAccess;
import com.agriinsight.backend.operations.application.ActivityLogPage;
import com.agriinsight.backend.operations.application.ActivityLogReadStore;
import com.agriinsight.backend.operations.application.ActivityLogRecord;
import com.agriinsight.backend.operations.application.ActivityReadPageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresActivityLogReadStore implements ActivityLogReadStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresActivityLogReadStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public ActivityLogPage findAll(
            ScopeContext scope,
            UUID activityId,
            ActivityLogAccess access,
            ActivityReadPageQuery query) {
        ScopeContext required = ActivityLogScope.require(scope, activityId);
        Objects.requireNonNull(access, "access is required");
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ").append(ActivityLogRowMapping.COLUMNS)
                .append(" FROM activity_logs WHERE tenant_id = ? AND activity_id = ?");
        List<Object> parameters = new ArrayList<>(List.of(required.tenantId(), activityId));
        appendVisibility(sql, parameters, required, access);
        sql.append(" ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        return page(jdbcTemplate.query(
                sql.toString(), ActivityLogRowMapping.MAPPER, parameters.toArray()), query);
    }

    @Override
    public ActivityLogPage findHistory(
            ScopeContext scope,
            UUID activityId,
            UUID logId,
            ActivityLogAccess access,
            ActivityReadPageQuery query) {
        ScopeContext required = ActivityLogScope.require(scope, activityId);
        Objects.requireNonNull(logId, "logId is required");
        Objects.requireNonNull(access, "access is required");
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("""
                WITH RECURSIVE ancestors AS (
                    SELECT * FROM activity_logs
                     WHERE tenant_id = ? AND activity_id = ? AND id = ?
                    UNION ALL
                    SELECT parent.*
                      FROM activity_logs AS parent
                      JOIN ancestors AS child
                        ON child.tenant_id = parent.tenant_id
                       AND child.activity_id = parent.activity_id
                       AND child.corrects_log_id = parent.id
                ), lineage AS (
                    SELECT * FROM ancestors WHERE corrects_log_id IS NULL
                    UNION ALL
                    SELECT child.*
                      FROM activity_logs AS child
                      JOIN lineage AS parent
                        ON child.tenant_id = parent.tenant_id
                       AND child.activity_id = parent.activity_id
                       AND child.corrects_log_id = parent.id
                )
                SELECT %s FROM lineage
                WHERE tenant_id = ? AND activity_id = ?
                """.formatted(ActivityLogRowMapping.COLUMNS));
        List<Object> parameters = new ArrayList<>(List.of(
                required.tenantId(), activityId, logId, required.tenantId(), activityId));
        appendVisibility(sql, parameters, required, access);
        sql.append(" ORDER BY occurred_at, id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        return page(jdbcTemplate.query(
                sql.toString(), ActivityLogRowMapping.MAPPER, parameters.toArray()), query);
    }

    private void appendVisibility(
            StringBuilder sql,
            List<Object> parameters,
            ScopeContext scope,
            ActivityLogAccess access) {
        if (access.manager()) {
            return;
        }
        sql.append(" AND (author_profile_id = ? OR employee_id = ?)");
        parameters.add(scope.profileId());
        parameters.add(access.workerEmployeeId().orElseThrow());
    }

    private ActivityLogPage page(
            List<ActivityLogRecord> rows,
            ActivityReadPageQuery query) {
        boolean hasMore = rows.size() > query.limit();
        List<ActivityLogRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new ActivityLogPage(items, query.limit(), query.offset(), hasMore);
    }
}
