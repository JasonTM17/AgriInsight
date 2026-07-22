package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.cost.application.CostSummaryGroup;

record CostSummarySqlGroup(
        String groupId,
        String groupKey,
        String budget,
        String groupBy,
        String requiredDimension) {

    static CostSummarySqlGroup from(CostSummaryGroup group) {
        return switch (group) {
            case MONTH -> new CostSummarySqlGroup(
                    "NULL::uuid",
                    "to_char(date_trunc('month', entry.occurred_at AT TIME ZONE 'UTC'), 'YYYY-MM')",
                    "NULL::numeric",
                    "to_char(date_trunc('month', entry.occurred_at AT TIME ZONE 'UTC'), 'YYYY-MM')",
                    "TRUE");
            case FARM -> new CostSummarySqlGroup(
                    "resolved_farm.id",
                    "resolved_farm.code",
                    "NULL::numeric",
                    "resolved_farm.id, resolved_farm.code",
                    "resolved_farm.id IS NOT NULL");
            case SEASON -> new CostSummarySqlGroup(
                    "resolved_season.id",
                    "resolved_season.code",
                    "resolved_season.budget_vnd",
                    "resolved_season.id, resolved_season.code, resolved_season.budget_vnd",
                    "resolved_season.id IS NOT NULL");
            case CATEGORY -> new CostSummarySqlGroup(
                    "NULL::uuid",
                    "entry.category_code",
                    "NULL::numeric",
                    "entry.category_code",
                    "TRUE");
        };
    }
}
