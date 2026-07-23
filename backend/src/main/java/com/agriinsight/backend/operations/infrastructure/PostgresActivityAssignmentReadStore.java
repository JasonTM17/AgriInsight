package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityAssignmentPage;
import com.agriinsight.backend.operations.application.ActivityAssignmentReadStore;
import com.agriinsight.backend.operations.application.ActivityAssignmentRecord;
import com.agriinsight.backend.operations.application.ActivityReadPageQuery;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresActivityAssignmentReadStore implements ActivityAssignmentReadStore {

    private static final String COLUMNS = "id, tenant_id, activity_id, employee_id, revoked_at, version";
    private static final RowMapper<ActivityAssignmentRecord> MAPPER = (result, rowNumber) -> {
        Timestamp revokedAt = result.getTimestamp("revoked_at");
        return new ActivityAssignmentRecord(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("activity_id", UUID.class),
                result.getObject("employee_id", UUID.class),
                revokedAt == null ? Optional.empty() : Optional.of(revokedAt.toInstant()),
                result.getLong("version"));
    };

    private final JdbcTemplate jdbcTemplate;

    public PostgresActivityAssignmentReadStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public ActivityAssignmentPage findAll(
            ScopeContext scope,
            UUID activityId,
            Optional<UUID> employeeId,
            ActivityReadPageQuery query) {
        ScopeContext required = ActivityLogScope.require(scope, activityId);
        Objects.requireNonNull(employeeId, "employeeId is required");
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM activity_assignees WHERE tenant_id = ? AND activity_id = ?");
        List<Object> parameters = new ArrayList<>(List.of(required.tenantId(), activityId));
        employeeId.ifPresent(id -> {
            sql.append(" AND employee_id = ?");
            parameters.add(id);
        });
        sql.append(" ORDER BY revoked_at NULLS FIRST, employee_id, id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<ActivityAssignmentRecord> rows = jdbcTemplate.query(
                sql.toString(), MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<ActivityAssignmentRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new ActivityAssignmentPage(items, query.limit(), query.offset(), hasMore);
    }
}
