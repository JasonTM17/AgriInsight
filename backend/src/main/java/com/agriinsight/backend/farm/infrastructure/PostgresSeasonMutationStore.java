package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.SeasonCommands;
import com.agriinsight.backend.farm.application.SeasonRecord;
import com.agriinsight.backend.farm.domain.Season;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

final class PostgresSeasonMutationStore {

    private final JdbcTemplate jdbcTemplate;

    PostgresSeasonMutationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    Optional<SeasonRecord> update(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            SeasonCommands.Update command) {
        requireVersion(expectedVersion);
        Objects.requireNonNull(command, "command is required");
        List<ColumnValue> columns = columns(command);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one season field must be provided");
        }
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope);
        if (!FarmScopeSql.lockWriteAuthorization(
                jdbcTemplate, writeScope, writeScope.resourceId().orElse(null))) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("UPDATE seasons AS season SET ");
        List<Object> parameters = new ArrayList<>();
        appendAssignments(sql, parameters, columns);
        sql.append("""
                , version = season.version + 1, updated_at = CURRENT_TIMESTAMP
                  FROM farms AS farm
                 WHERE farm.tenant_id = season.tenant_id
                   AND farm.id = season.farm_id
                   AND season.tenant_id = ?
                   AND season.id = ?
                   AND season.version = ?
                   AND (
                """);
        parameters.add(writeScope.tenantId());
        parameters.add(Objects.requireNonNull(seasonId, "seasonId is required"));
        parameters.add(expectedVersion);
        appendDifferencePredicate(sql, parameters, columns);
        sql.append(')');
        FarmScopeSql.append(sql, parameters, writeScope, writeScope.resourceId().orElse(null));
        sql.append(" RETURNING ").append(SeasonRowMapping.SELECT_COLUMNS);
        return SeasonRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), SeasonRowMapping.MAPPER, parameters.toArray()));
    }

    Optional<SeasonRecord> transition(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            Season.Status sourceStatus,
            Season.Status targetStatus,
            LocalDate effectiveDate) {
        requireVersion(expectedVersion);
        Season.Status source = Objects.requireNonNull(sourceStatus, "sourceStatus is required");
        Season.Status target = Objects.requireNonNull(targetStatus, "targetStatus is required");
        if (!source.canTransitionTo(target)) {
            throw new IllegalArgumentException("Season transition is not allowed");
        }
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope);
        if (!FarmScopeSql.lockWriteAuthorization(
                jdbcTemplate, writeScope, writeScope.resourceId().orElse(null))) {
            return Optional.empty();
        }
        String dateAssignments = target == Season.Status.ACTIVE
                ? "started_on = ?, ended_on = NULL"
                : "ended_on = ?";
        StringBuilder sql = new StringBuilder("UPDATE seasons AS season SET status = ?, ")
                .append(dateAssignments)
                .append("""
                        , version = season.version + 1, updated_at = CURRENT_TIMESTAMP
                          FROM farms AS farm
                         WHERE farm.tenant_id = season.tenant_id
                           AND farm.id = season.farm_id
                           AND season.tenant_id = ?
                           AND season.id = ?
                           AND season.version = ?
                           AND season.status = ?
                        """);
        List<Object> parameters = new ArrayList<>(List.of(
                target.name(),
                Objects.requireNonNull(effectiveDate, "effectiveDate is required"),
                writeScope.tenantId(),
                Objects.requireNonNull(seasonId, "seasonId is required"),
                expectedVersion,
                source.name()));
        FarmScopeSql.append(sql, parameters, writeScope, writeScope.resourceId().orElse(null));
        sql.append(" RETURNING ").append(SeasonRowMapping.SELECT_COLUMNS);
        return SeasonRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), SeasonRowMapping.MAPPER, parameters.toArray()));
    }

    private List<ColumnValue> columns(SeasonCommands.Update command) {
        List<ColumnValue> columns = new ArrayList<>();
        command.code().ifPresent(value -> columns.add(new ColumnValue("code", value)));
        command.displayName().ifPresent(value -> columns.add(new ColumnValue("display_name", value)));
        command.varietyName().ifPresent(value -> columns.add(new ColumnValue(
                "variety_name", nullable(value.orElse(null), Types.VARCHAR))));
        command.plannedStartDate().ifPresent(value -> columns.add(new ColumnValue("planned_start_date", value)));
        command.plannedEndDate().ifPresent(value -> columns.add(new ColumnValue("planned_end_date", value)));
        command.plantedAreaHectares().ifPresent(value -> columns.add(new ColumnValue("planted_area_hectares", value)));
        command.budgetVnd().ifPresent(value -> columns.add(new ColumnValue(
                "budget_vnd", nullable(value.orElse(null), Types.NUMERIC))));
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
            sql.append("season.").append(column.name()).append(" IS DISTINCT FROM ?");
            parameters.add(column.value());
        }
    }

    private Object nullable(Object value, int sqlType) {
        return value == null ? new SqlParameterValue(sqlType, null) : value;
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private record ColumnValue(String name, Object value) {
    }
}
