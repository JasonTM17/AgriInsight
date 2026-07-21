package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FieldCommands;
import com.agriinsight.backend.farm.application.FieldPage;
import com.agriinsight.backend.farm.application.FieldQuery;
import com.agriinsight.backend.farm.application.FieldRecord;
import com.agriinsight.backend.farm.application.FieldStore;
import com.agriinsight.backend.farm.domain.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresFieldStore implements FieldStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresFieldCreateStore creates;
    private final PostgresFieldMutationStore mutations;
    private final PostgresFieldLifecycleStore lifecycle;

    public PostgresFieldStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.creates = new PostgresFieldCreateStore(jdbcTemplate);
        this.mutations = new PostgresFieldMutationStore(jdbcTemplate);
        this.lifecycle = new PostgresFieldLifecycleStore(jdbcTemplate);
    }

    @Override
    public FieldPage findAll(ScopeContext scope, FieldQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE field.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        FarmScopeSql.append(sql, parameters, scope, query.farmId().orElse(null));
        query.farmId().ifPresent(id -> addFilter(sql, parameters, "field.farm_id", id));
        query.active().ifPresent(active -> addFilter(sql, parameters, "field.active", active));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(field.code)) > 0")
                    .append(" OR position(lower(?) in lower(field.display_name)) > 0)");
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY lower(field.display_name), field.code, field.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<FieldRecord> rows = jdbcTemplate.query(
                sql.toString(), FieldRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<FieldRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new FieldPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<FieldRecord> findById(ScopeContext scope, UUID fieldId) {
        StringBuilder sql = baseSelect()
                .append(" WHERE field.tenant_id = ? AND field.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        parameters.add(Objects.requireNonNull(fieldId, "fieldId is required"));
        FarmScopeSql.append(sql, parameters, scope, null);
        return FieldRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), FieldRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            Optional<UUID> responsibleEmployeeId) {
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("""
                SELECT 1 FROM farms AS farm
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND farm.active
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                requireScope(scope).tenantId(), requiredFarmId));
        Objects.requireNonNull(responsibleEmployeeId, "responsibleEmployeeId is required")
                .ifPresent(employeeId -> {
                    sql.append("""
                             AND EXISTS (
                                   SELECT 1 FROM employees AS employee
                                    WHERE employee.tenant_id = farm.tenant_id
                                      AND employee.id = ?
                                      AND employee.active)
                            """);
                    parameters.add(employeeId);
                });
        FarmScopeSql.append(sql, parameters, scope, requiredFarmId);
        return !jdbcTemplate.query(sql.toString(), (result, rowNumber) -> 1, parameters.toArray()).isEmpty();
    }

    @Override
    public FieldRecord create(ScopeContext scope, Field field) {
        return creates.create(scope, field);
    }

    @Override
    public Optional<FieldRecord> update(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            FieldCommands.Update command) {
        return mutations.update(scope, fieldId, expectedVersion, command);
    }

    @Override
    public Optional<FieldRecord> updateActive(
            ScopeContext scope,
            UUID fieldId,
            long expectedVersion,
            boolean active) {
        return lifecycle.updateActive(scope, fieldId, expectedVersion, active);
    }

    @Override
    public boolean hasDeactivationBlockers(ScopeContext scope, UUID fieldId) {
        return lifecycle.hasDeactivationBlockers(scope, fieldId);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(FieldRowMapping.SELECT_COLUMNS)
                .append(" FROM fields AS field JOIN farms AS farm")
                .append(" ON farm.tenant_id = field.tenant_id AND farm.id = field.farm_id");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }
}
