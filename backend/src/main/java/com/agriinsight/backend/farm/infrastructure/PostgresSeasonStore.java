package com.agriinsight.backend.farm.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.SeasonCommands;
import com.agriinsight.backend.farm.application.SeasonPage;
import com.agriinsight.backend.farm.application.SeasonQuery;
import com.agriinsight.backend.farm.application.SeasonRecord;
import com.agriinsight.backend.farm.application.SeasonStore;
import com.agriinsight.backend.farm.domain.Season;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
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
public class PostgresSeasonStore implements SeasonStore {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresSeasonMutationStore mutations;

    public PostgresSeasonStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.mutations = new PostgresSeasonMutationStore(jdbcTemplate);
    }

    @Override
    public SeasonPage findAll(ScopeContext scope, SeasonQuery query) {
        Objects.requireNonNull(query, "query is required");
        StringBuilder sql = baseSelect().append(" WHERE season.tenant_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        FarmScopeSql.append(sql, parameters, scope, query.farmId().orElse(null));
        query.farmId().ifPresent(id -> addFilter(sql, parameters, "season.farm_id", id));
        query.fieldId().ifPresent(id -> addFilter(sql, parameters, "season.field_id", id));
        query.cropId().ifPresent(id -> addFilter(sql, parameters, "season.crop_id", id));
        query.status().ifPresent(status -> addFilter(sql, parameters, "season.status", status.name()));
        query.search().ifPresent(search -> {
            sql.append(" AND (position(lower(?) in lower(season.code)) > 0")
                    .append(" OR position(lower(?) in lower(season.display_name)) > 0")
                    .append(" OR position(lower(?) in lower(COALESCE(season.variety_name, ''))) > 0)");
            parameters.add(search);
            parameters.add(search);
            parameters.add(search);
        });
        sql.append(" ORDER BY season.planned_start_date DESC, season.code, season.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<SeasonRecord> rows = jdbcTemplate.query(
                sql.toString(), SeasonRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        List<SeasonRecord> items = hasMore ? rows.subList(0, query.limit()) : rows;
        return new SeasonPage(items, query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<SeasonRecord> findById(ScopeContext scope, UUID seasonId) {
        UUID requiredSeasonId = Objects.requireNonNull(seasonId, "seasonId is required");
        StringBuilder sql = baseSelect()
                .append(" WHERE season.tenant_id = ? AND season.id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(requireScope(scope).tenantId());
        parameters.add(requiredSeasonId);
        FarmScopeSql.append(sql, parameters, scope, null);
        return SeasonRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), SeasonRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public boolean farmVisible(ScopeContext scope, UUID farmId) {
        return FarmScopeSql.farmVisible(jdbcTemplate, scope, farmId);
    }

    @Override
    public boolean liveParentsAvailable(
            ScopeContext scope,
            UUID farmId,
            UUID fieldId,
            UUID cropId,
            BigDecimal plantedAreaHectares) {
        UUID requiredFarmId = Objects.requireNonNull(farmId, "farmId is required");
        StringBuilder sql = new StringBuilder("""
                SELECT 1
                  FROM farms AS farm
                  JOIN fields AS field
                    ON field.tenant_id = farm.tenant_id
                   AND field.farm_id = farm.id
                  JOIN crops AS crop
                    ON crop.tenant_id = farm.tenant_id
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                   AND field.id = ?
                   AND crop.id = ?
                   AND farm.active
                   AND field.active
                   AND crop.active
                   AND field.area_hectares >= ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                requireScope(scope).tenantId(),
                requiredFarmId,
                Objects.requireNonNull(fieldId, "fieldId is required"),
                Objects.requireNonNull(cropId, "cropId is required"),
                Objects.requireNonNull(plantedAreaHectares, "plantedAreaHectares is required")));
        FarmScopeSql.append(sql, parameters, scope, requiredFarmId);
        return !jdbcTemplate.query(sql.toString(), (result, rowNumber) -> 1, parameters.toArray()).isEmpty();
    }

    @Override
    public Optional<SeasonRecord> create(ScopeContext scope, Season season) {
        Objects.requireNonNull(season, "season is required");
        ScopeContext writeScope = FarmScopeSql.requireWriteScope(scope, season.farmId());
        if (!writeScope.tenantId().equals(season.tenantId())) {
            throw new IllegalArgumentException("Season cannot switch tenants");
        }
        if (!FarmScopeSql.lockWriteAuthorization(jdbcTemplate, writeScope, season.farmId())) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    variety_name, planned_start_date, planned_end_date,
                    planted_area_hectares, budget_vnd)
                SELECT ?, farm.tenant_id, farm.id, ?, ?, ?, ?, ?, ?, ?, ?, ?
                  FROM farms AS farm
                 WHERE farm.tenant_id = ?
                   AND farm.id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(
                season.id(), season.fieldId(), season.cropId(), season.code(),
                season.displayName(), nullable(season.varietyName().orElse(null), Types.VARCHAR),
                season.plannedStartDate(), season.plannedEndDate(),
                season.plantedAreaHectares(), nullable(season.budgetVnd().orElse(null), Types.NUMERIC),
                season.tenantId(), season.farmId()));
        FarmScopeSql.append(sql, parameters, writeScope, season.farmId());
        sql.append(" RETURNING ").append(SeasonRowMapping.RETURNING_COLUMNS);
        return SeasonRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), SeasonRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public Optional<SeasonRecord> update(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            SeasonCommands.Update command) {
        return mutations.update(scope, seasonId, expectedVersion, command);
    }

    @Override
    public Optional<SeasonRecord> transition(
            ScopeContext scope,
            UUID seasonId,
            long expectedVersion,
            Season.Status sourceStatus,
            Season.Status targetStatus,
            LocalDate effectiveDate) {
        return mutations.transition(
                scope, seasonId, expectedVersion, sourceStatus, targetStatus, effectiveDate);
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ")
                .append(SeasonRowMapping.SELECT_COLUMNS)
                .append(" FROM seasons AS season JOIN farms AS farm")
                .append(" ON farm.tenant_id = season.tenant_id AND farm.id = season.farm_id");
    }

    private void addFilter(StringBuilder sql, List<Object> parameters, String column, Object value) {
        sql.append(" AND ").append(column).append(" = ?");
        parameters.add(value);
    }

    private ScopeContext requireScope(ScopeContext scope) {
        return Objects.requireNonNull(scope, "scope is required");
    }

    private Object nullable(Object value, int sqlType) {
        return value == null ? new SqlParameterValue(sqlType, null) : value;
    }
}
