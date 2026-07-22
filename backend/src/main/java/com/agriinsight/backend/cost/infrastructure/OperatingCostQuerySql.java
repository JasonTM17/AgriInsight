package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.cost.application.CostSummaryQuery;
import com.agriinsight.backend.cost.application.OperatingCostQuery;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class OperatingCostQuerySql {

    static final String HIERARCHY_FROM = """
             FROM operating_cost_entries AS entry
             LEFT JOIN fields AS target_field
               ON entry.target_type = 'FIELD'
              AND target_field.tenant_id = entry.tenant_id
              AND target_field.id = entry.field_id
             LEFT JOIN seasons AS target_season
               ON entry.target_type = 'SEASON'
              AND target_season.tenant_id = entry.tenant_id
              AND target_season.id = entry.season_id
             LEFT JOIN activities AS target_activity
               ON entry.target_type = 'ACTIVITY'
              AND target_activity.tenant_id = entry.tenant_id
              AND target_activity.id = entry.activity_id
             LEFT JOIN farms AS resolved_farm
               ON resolved_farm.tenant_id = entry.tenant_id
              AND resolved_farm.id = CASE entry.target_type
                    WHEN 'FARM' THEN entry.farm_id
                    WHEN 'FIELD' THEN target_field.farm_id
                    WHEN 'SEASON' THEN target_season.farm_id
                    WHEN 'ACTIVITY' THEN target_activity.farm_id
                    ELSE NULL
                  END
             LEFT JOIN seasons AS resolved_season
               ON resolved_season.tenant_id = entry.tenant_id
              AND resolved_season.id = CASE entry.target_type
                    WHEN 'SEASON' THEN entry.season_id
                    WHEN 'ACTIVITY' THEN target_activity.season_id
                    ELSE NULL
                  END
            """;

    private OperatingCostQuerySql() {
    }

    static Query forList(java.util.UUID tenantId, OperatingCostQuery query) {
        Query sql = bounded(tenantId, query.occurredFrom(), query.occurredTo());
        add(sql, "resolved_farm.id", query.farmId());
        add(sql, """
                CASE entry.target_type
                    WHEN 'FIELD' THEN entry.field_id
                    WHEN 'SEASON' THEN target_season.field_id
                    WHEN 'ACTIVITY' THEN target_activity.field_id
                    ELSE NULL
                END
                """, query.fieldId());
        add(sql, "resolved_season.id", query.seasonId());
        add(sql, "entry.activity_id", query.activityId());
        query.category().ifPresent(value -> sql.add("entry.category_code = ?", value.name()));
        query.targetType().ifPresent(value -> sql.add("entry.target_type = ?", value.name()));
        query.entryKind().ifPresent(value -> sql.add("entry.entry_kind = ?", value.name()));
        return sql;
    }

    static Query forSummary(java.util.UUID tenantId, CostSummaryQuery query) {
        Query sql = bounded(tenantId, query.occurredFrom(), query.occurredTo());
        add(sql, "resolved_farm.id", query.farmId());
        add(sql, "resolved_season.id", query.seasonId());
        query.category().ifPresent(value -> sql.add("entry.category_code = ?", value.name()));
        return sql;
    }

    private static Query bounded(
            java.util.UUID tenantId,
            java.time.Instant occurredFrom,
            java.time.Instant occurredTo) {
        Query query = new Query(new StringBuilder(" WHERE entry.tenant_id = ?"),
                new ArrayList<>(List.of(
                        tenantId,
                        Timestamp.from(occurredFrom),
                        Timestamp.from(occurredTo))));
        query.sql().append(" AND entry.occurred_at >= ? AND entry.occurred_at < ?");
        return query;
    }

    private static void add(
            Query query, String expression, Optional<java.util.UUID> value) {
        value.ifPresent(id -> query.add(expression + " = ?", id));
    }

    record Query(StringBuilder sql, List<Object> parameters) {

        Query add(String condition, Object value) {
            sql.append(" AND ").append(condition);
            parameters.add(value);
            return this;
        }
    }
}
