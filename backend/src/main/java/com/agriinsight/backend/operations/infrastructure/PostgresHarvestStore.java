package com.agriinsight.backend.operations.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.HarvestPage;
import com.agriinsight.backend.operations.application.HarvestQuery;
import com.agriinsight.backend.operations.application.HarvestRecord;
import com.agriinsight.backend.operations.application.HarvestStore;
import com.agriinsight.backend.operations.domain.Harvest;
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
public class PostgresHarvestStore implements HarvestStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresHarvestStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public HarvestPage findAll(ScopeContext scope, HarvestQuery query) {
        StringBuilder sql = baseSelect().append(" WHERE harvest.tenant_id = ?");
        List<Object> parameters = new ArrayList<>(List.of(scope.tenantId()));
        HarvestScopeSql.appendRead(sql, parameters, scope);
        addOptional(sql, parameters, "harvest.farm_id", query.farmId());
        addOptional(sql, parameters, "harvest.field_id", query.fieldId());
        addOptional(sql, parameters, "harvest.season_id", query.seasonId());
        addOptional(sql, parameters, "harvest.crop_id", query.cropId());
        query.occurredFrom().ifPresent(value -> add(sql, parameters, "harvest.occurred_on >= ?", value));
        query.occurredTo().ifPresent(value -> add(sql, parameters, "harvest.occurred_on <= ?", value));
        sql.append(" ORDER BY harvest.occurred_on DESC, harvest.id LIMIT ? OFFSET ?");
        parameters.add(query.limit() + 1);
        parameters.add(query.offset());
        List<HarvestRecord> rows = jdbcTemplate.query(
                sql.toString(), HarvestRowMapping.MAPPER, parameters.toArray());
        boolean hasMore = rows.size() > query.limit();
        return new HarvestPage(
                hasMore ? rows.subList(0, query.limit()) : rows,
                query.limit(), query.offset(), hasMore);
    }

    @Override
    public Optional<HarvestRecord> findById(ScopeContext scope, UUID harvestId) {
        StringBuilder sql = baseSelect()
                .append(" WHERE harvest.tenant_id = ? AND harvest.id = ?");
        List<Object> parameters = new ArrayList<>(List.of(
                scope.tenantId(), Objects.requireNonNull(harvestId, "harvestId is required")));
        HarvestScopeSql.appendRead(sql, parameters, scope);
        return HarvestRowMapping.exactlyOneOrEmpty(
                jdbcTemplate.query(sql.toString(), HarvestRowMapping.MAPPER, parameters.toArray()));
    }

    @Override
    public boolean farmVisible(ScopeContext scope, UUID farmId) {
        return HarvestScopeSql.farmVisible(jdbcTemplate, scope, farmId);
    }

    @Override
    public boolean postTargetAvailable(
            ScopeContext scope, UUID farmId, UUID fieldId,
            UUID seasonId, UUID cropId, LocalDate occurredOn) {
        HarvestScopeSql.requireWriteScope(scope, farmId);
        if (!farmVisible(scope, farmId)) {
            return false;
        }
        return !jdbcTemplate.query("""
                SELECT season.id
                  FROM farms AS farm
                  JOIN seasons AS season
                    ON season.tenant_id = farm.tenant_id AND season.farm_id = farm.id
                 WHERE farm.tenant_id = ? AND farm.id = ? AND farm.active
                   AND season.id = ? AND season.field_id = ? AND season.crop_id = ?
                   AND season.status IN ('ACTIVE', 'COMPLETED')
                   AND ? >= season.started_on
                   AND (season.ended_on IS NULL OR ? <= season.ended_on)
                """, (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), farmId, seasonId, fieldId, cropId, occurredOn, occurredOn).isEmpty();
    }

    @Override
    public Optional<HarvestRecord> append(ScopeContext scope, Harvest harvest) {
        Harvest value = Objects.requireNonNull(harvest, "harvest is required");
        ScopeContext required = HarvestScopeSql.requireWriteScope(scope, value.farmId());
        if (!required.tenantId().equals(value.tenantId())
                || !required.profileId().equals(value.recordedByProfileId())) {
            throw new IllegalArgumentException("Harvest cannot switch tenant or recorder");
        }
        if (!HarvestScopeSql.lockWriteAuthorization(jdbcTemplate, required, value.farmId())) {
            return Optional.empty();
        }
        return value.correctsHarvestId().isPresent()
                ? appendCorrection(required, value) : appendOriginal(required, value);
    }

    private Optional<HarvestRecord> appendOriginal(ScopeContext scope, Harvest harvest) {
        if (!postTargetAvailable(
                scope, harvest.farmId(), harvest.fieldId(), harvest.seasonId(),
                harvest.cropId(), harvest.occurredOn())) {
            return Optional.empty();
        }
        if (!lockSeason(scope, harvest)) {
            return Optional.empty();
        }
        return insert(harvest);
    }

    private Optional<HarvestRecord> appendCorrection(ScopeContext scope, Harvest harvest) {
        String sql = """
                INSERT INTO harvests (
                    id, tenant_id, farm_id, field_id, season_id, crop_id, recorded_by_profile_id,
                    occurred_on, quantity_kg, waste_quantity_kg, quality_grade, revenue_vnd,
                    corrects_harvest_id, correction_kind, correction_reason)
                SELECT ?, original.tenant_id, original.farm_id, original.field_id,
                       original.season_id, original.crop_id, ?, ?, ?, ?, ?, ?, original.id, ?, ?
                  FROM harvests AS original
                 WHERE original.tenant_id = ? AND original.id = ? AND original.farm_id = ?
                   AND original.field_id = ? AND original.season_id = ? AND original.crop_id = ?
                   AND NOT EXISTS (
                       SELECT 1 FROM harvests AS successor
                        WHERE successor.tenant_id = original.tenant_id
                          AND successor.corrects_harvest_id = original.id)
                RETURNING %s
                """.formatted(HarvestRowMapping.COLUMNS);
        return HarvestRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql, HarvestRowMapping.MAPPER,
                harvest.id(), harvest.recordedByProfileId(), harvest.occurredOn(),
                harvest.quantityKg(), harvest.wasteQuantityKg(),
                nullable(harvest.qualityGrade().orElse(null), Types.VARCHAR),
                nullable(harvest.revenueVnd().orElse(null), Types.NUMERIC),
                harvest.correctionKind().orElseThrow().name(), harvest.correctionReason().orElseThrow(),
                scope.tenantId(), harvest.correctsHarvestId().orElseThrow(), harvest.farmId(),
                harvest.fieldId(), harvest.seasonId(), harvest.cropId()));
    }

    private Optional<HarvestRecord> insert(Harvest harvest) {
        String sql = """
                INSERT INTO harvests (
                    id, tenant_id, farm_id, field_id, season_id, crop_id, recorded_by_profile_id,
                    occurred_on, quantity_kg, waste_quantity_kg, quality_grade, revenue_vnd,
                    corrects_harvest_id, correction_kind, correction_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(HarvestRowMapping.COLUMNS);
        return HarvestRowMapping.exactlyOneOrEmpty(jdbcTemplate.query(
                sql, HarvestRowMapping.MAPPER,
                harvest.id(), harvest.tenantId(), harvest.farmId(), harvest.fieldId(),
                harvest.seasonId(), harvest.cropId(), harvest.recordedByProfileId(),
                harvest.occurredOn(), harvest.quantityKg(), harvest.wasteQuantityKg(),
                nullable(harvest.qualityGrade().orElse(null), Types.VARCHAR),
                nullable(harvest.revenueVnd().orElse(null), Types.NUMERIC),
                nullable(null, Types.OTHER), nullable(null, Types.VARCHAR), nullable(null, Types.VARCHAR)));
    }

    private boolean lockSeason(ScopeContext scope, Harvest harvest) {
        List<UUID> rows = jdbcTemplate.query("""
                SELECT season.id
                  FROM farms AS farm
                  JOIN seasons AS season
                    ON season.tenant_id = farm.tenant_id AND season.farm_id = farm.id
                 WHERE season.tenant_id = ? AND season.id = ? AND season.farm_id = ?
                   AND season.field_id = ? AND season.crop_id = ?
                   AND season.status IN ('ACTIVE', 'COMPLETED')
                   AND farm.active
                   AND ? >= season.started_on
                   AND (season.ended_on IS NULL OR ? <= season.ended_on)
                 FOR SHARE OF farm, season
                """, (result, rowNumber) -> result.getObject("id", UUID.class),
                scope.tenantId(), harvest.seasonId(), harvest.farmId(),
                harvest.fieldId(), harvest.cropId(), harvest.occurredOn(), harvest.occurredOn());
        if (rows.size() > 1) {
            throw new IllegalStateException("Harvest target returned multiple seasons");
        }
        return !rows.isEmpty();
    }

    private StringBuilder baseSelect() {
        return new StringBuilder("SELECT ").append(HarvestRowMapping.SELECT_COLUMNS)
                .append(" FROM harvests AS harvest JOIN farms AS farm")
                .append(" ON farm.tenant_id = harvest.tenant_id AND farm.id = harvest.farm_id");
    }

    private void addOptional(
            StringBuilder sql, List<Object> parameters, String column, Optional<UUID> value) {
        value.ifPresent(id -> add(sql, parameters, column + " = ?", id));
    }

    private void add(StringBuilder sql, List<Object> parameters, String condition, Object value) {
        sql.append(" AND ").append(condition);
        parameters.add(value);
    }

    private Object nullable(Object value, int type) {
        return value == null ? new SqlParameterValue(type, null) : value;
    }
}
