package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityCommands;
import com.agriinsight.backend.operations.application.ActivityPage;
import com.agriinsight.backend.operations.application.ActivityQuery;
import com.agriinsight.backend.operations.application.ActivityRecord;
import com.agriinsight.backend.operations.application.ActivityStore;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.operations.domain.ActivityType;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
public class PostgresActivityStore implements ActivityStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresActivityMutationStore mutations;

    public PostgresActivityStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresActivityMutationStore(jdbcTemplate);
    }

    @Override
    public ActivityPage findAll(ScopeContext scope, ActivityQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE activity.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        ActivityScopeSql.appendRead(sql, parameters, scope);
        query.farmId().ifPresent(id -> addFilter(sql, parameters, "activity.farm_id", id));
        query.fieldId().ifPresent(id -> addFilter(sql, parameters, "activity.field_id", id));
        query.seasonId().ifPresent(id -> addFilter(sql, parameters, "activity.season_id", id));
        query.activityType().ifPresent(type ->
                addFilter(sql, parameters, "activity.activity_type_code", type.name()));
        query.status().ifPresent(status -> addFilter(sql, parameters, "activity.status", status.name()));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(activity.code)) > 0")
                    .append(" OR position(lower(?) in lower(activity.title)) > 0")
                    .append(" OR position(lower(?) in lower(COALESCE(activity.description, ''))) > 0)");
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY activity.due_at, activity.code, activity.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<ActivityRecord> rows = jdbcTemplate.query(
                sql.toString(), ActivityRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<ActivityRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new ActivityPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<ActivityRecord> findById(ScopeContext scope, UUID activityId) {
        StringBuilder sql = baseSelect().append(" WHERE activity.tenant_id = ? AND activity.id = ?");
        List<Object> parameters = new ArrayList<>(List.of(
                requireScope(scope).tenantId(),
                Objects.requireNonNull(activityId, "activityId is required")));
        ActivityScopeSql.appendRead(sql, parameters, scope);
        return ActivityRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), ActivityRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public boolean farmVisible(ScopeContext scope, UUID farmId) {
        return ActivityScopeSql.farmVisible(jdbcTemplate, scope, farmId);
    }

    @Override
    public boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID seasonId,
            ActivityType activityType) {
        ScopeContext writeScope = ActivityScopeSql.requireWriteScope(scope, farmId);
        if (!ActivityScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, farmId)) {
            return false;
        }
        return !jdbcTemplate.query("""
                SELECT season.id
                  FROM farms AS farm
                  JOIN fields AS field
                    ON field.tenant_id = farm.tenant_id
                   AND field.farm_id = farm.id
                   AND field.id = ?
                   AND field.active
                  JOIN seasons AS season
                    ON season.tenant_id = field.tenant_id
                   AND season.farm_id = field.farm_id
                   AND season.field_id = field.id
                   AND season.id = ?
                   AND season.status IN ('PLANNED', 'ACTIVE')
                  JOIN activity_types AS activity_type
                    ON activity_type.tenant_id = season.tenant_id
                   AND activity_type.code = ?
                   AND activity_type.active
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.active
                """,
                (result, rowNumber) -> result.getObject("id", UUID.class),
                Objects.requireNonNull(fieldId, "fieldId is required"),
                Objects.requireNonNull(seasonId, "seasonId is required"),
                Objects.requireNonNull(activityType, "activityType is required").name(),
                writeScope.tenantId(), farmId).isEmpty();
    }

    @Override
    public Optional<ActivityRecord> create(ScopeContext scope, Activity activity) {
        Objects.requireNonNull(activity, "activity is required");
        ScopeContext writeScope = ActivityScopeSql.requireWriteScope(scope, activity.farmId());
        if (!writeScope.tenantId().equals(activity.tenantId())) {
            throw new IllegalArgumentException("Activity cannot switch tenants");
        }
        if (!ActivityScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, activity.farmId())) {
            return Optional.empty();
        }
        return ActivityRowMapping.exactlyOneOrEmpty(jdbcTemplate.query("""
                INSERT INTO activities (
                    id, tenant_id, farm_id, field_id, season_id, activity_type_code,
                    code, title, description, planned_start_at, due_at)
                SELECT ?, farm.tenant_id, farm.id, field.id, season.id, activity_type.code,
                       ?, ?, ?, ?, ?
                  FROM farms AS farm
                  JOIN fields AS field
                    ON field.tenant_id = farm.tenant_id
                   AND field.farm_id = farm.id
                   AND field.id = ?
                   AND field.active
                  JOIN seasons AS season
                    ON season.tenant_id = field.tenant_id
                   AND season.farm_id = field.farm_id
                   AND season.field_id = field.id
                   AND season.id = ?
                   AND season.status IN ('PLANNED', 'ACTIVE')
                  JOIN activity_types AS activity_type
                    ON activity_type.tenant_id = season.tenant_id
                   AND activity_type.code = ?
                   AND activity_type.active
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.active
                RETURNING %s
                """.formatted(ActivityRowMapping.RETURNING_COLUMNS),
                ActivityRowMapping.MAPPER,
                activity.id(), activity.code(), activity.title(), nullable(activity.description().orElse(null)),
                Timestamp.from(activity.plannedStartAt()), Timestamp.from(activity.dueAt()),
                activity.fieldId(), activity.seasonId(),
                activity.activityType().name(), activity.tenantId(), activity.farmId()));
    }

    @Override
    public Optional<ActivityRecord> update(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityCommands.Update command) {
        return mutations.update(scope, activityId, expectedVersion, command);
    }

    @Override
    public Optional<ActivityRecord> transition(
            ScopeContext scope,
            UUID activityId,
            long expectedVersion,
            ActivityStatus sourceStatus,
            ActivityStatus targetStatus,
            Instant effectiveAt) {
        return mutations.transition(
                scope, activityId, expectedVersion, sourceStatus, targetStatus, effectiveAt);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(ActivityRowMapping.SELECT_COLUMNS)
                .append(" FROM activities AS activity JOIN farms AS farm")
                .append(" ON farm.tenant_id = activity.tenant_id AND farm.id = activity.farm_id");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }

    private Object nullable(String value) {
        return value == null ? new SqlParameterValue(Types.VARCHAR, null) : value;
    }
}
