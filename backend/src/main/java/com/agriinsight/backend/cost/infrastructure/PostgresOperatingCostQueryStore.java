package com.agriinsight.backend.cost.infrastructure;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.cost.application.CostSummaryItem;
import com.agriinsight.backend.cost.application.CostSummaryQuery;
import com.agriinsight.backend.cost.application.CostSummaryResult;
import com.agriinsight.backend.cost.application.OperatingCostPage;
import com.agriinsight.backend.cost.application.OperatingCostQuery;
import com.agriinsight.backend.cost.application.OperatingCostQueryStore;
import com.agriinsight.backend.cost.application.OperatingCostRecord;
import java.math.BigDecimal;
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
public class PostgresOperatingCostQueryStore implements OperatingCostQueryStore {

    private static final int SUMMARY_LIMIT = 500;
    private final JdbcTemplate jdbcTemplate;

    public PostgresOperatingCostQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public OperatingCostPage findAll(ScopeContext scope, OperatingCostQuery query) {
        ScopeContext required = OperatingCostReadScope.require(scope);
        OperatingCostQuery value = Objects.requireNonNull(query, "query is required");
        var filter = OperatingCostQuerySql.forList(required.tenantId(), value);
        appendTargetedScope(filter, required);
        String sql = "SELECT " + OperatingCostRowMapping.COLUMNS
                + OperatingCostQuerySql.HIERARCHY_FROM
                + filter.sql()
                + " ORDER BY entry.occurred_at DESC, entry.id DESC LIMIT ? OFFSET ?";
        filter.parameters().add(value.limit() + 1);
        filter.parameters().add(value.offset());
        List<OperatingCostRecord> rows = jdbcTemplate.query(
                sql, OperatingCostRowMapping.MAPPER, filter.parameters().toArray());
        boolean hasMore = rows.size() > value.limit();
        return new OperatingCostPage(
                hasMore ? rows.subList(0, value.limit()) : rows,
                value.limit(), value.offset(), hasMore);
    }

    @Override
    public Optional<OperatingCostRecord> findById(
            ScopeContext scope, UUID entryId) {
        ScopeContext required = OperatingCostReadScope.require(scope);
        List<OperatingCostRecord> rows = jdbcTemplate.query("""
                SELECT %s FROM operating_cost_entries AS entry
                 WHERE entry.tenant_id = ? AND entry.id = ?
                """.formatted(OperatingCostRowMapping.COLUMNS),
                OperatingCostRowMapping.MAPPER,
                required.tenantId(), Objects.requireNonNull(entryId, "entryId is required"));
        return OperatingCostRowMapping.exactlyOneOrEmpty(rows);
    }

    @Override
    public CostSummaryResult summarize(ScopeContext scope, CostSummaryQuery query) {
        ScopeContext required = OperatingCostReadScope.require(scope);
        CostSummaryQuery value = Objects.requireNonNull(query, "query is required");
        var filter = OperatingCostQuerySql.forSummary(required.tenantId(), value);
        appendTargetedScope(filter, required);
        CostSummarySqlGroup group = CostSummarySqlGroup.from(value.groupBy());
        filter.sql().append(" AND ").append(group.requiredDimension());
        String net = "SUM(CASE WHEN entry.entry_kind = 'POSTING' "
                + "THEN entry.amount_vnd ELSE -entry.amount_vnd END)";
        String variance = value.groupBy()
                        == com.agriinsight.backend.cost.application.CostSummaryGroup.SEASON
                ? group.budget() + " - " + net
                : "NULL::numeric";
        String sql = """
                SELECT %s AS group_id, %s AS group_key,
                       SUM(CASE WHEN entry.entry_kind = 'POSTING'
                                THEN entry.amount_vnd ELSE 0 END) AS posting_amount_vnd,
                       SUM(CASE WHEN entry.entry_kind = 'REVERSAL'
                                THEN entry.amount_vnd ELSE 0 END) AS reversal_amount_vnd,
                       %s AS net_operating_cost_vnd,
                       %s AS season_budget_vnd,
                       %s AS budget_variance_vnd
                %s%s
                 GROUP BY %s
                 ORDER BY group_key
                 LIMIT %d
                """.formatted(
                group.groupId(), group.groupKey(), net, group.budget(), variance,
                OperatingCostQuerySql.HIERARCHY_FROM, filter.sql(),
                group.groupBy(), SUMMARY_LIMIT + 1);
        List<CostSummaryItem> rows = jdbcTemplate.query(
                sql,
                (result, rowNumber) -> new CostSummaryItem(
                        Optional.ofNullable(result.getObject("group_id", UUID.class)),
                        result.getString("group_key"),
                        result.getBigDecimal("posting_amount_vnd"),
                        result.getBigDecimal("reversal_amount_vnd"),
                        result.getBigDecimal("net_operating_cost_vnd"),
                        optionalMoney(result.getBigDecimal("season_budget_vnd")),
                        optionalMoney(result.getBigDecimal("budget_variance_vnd"))),
                filter.parameters().toArray());
        boolean hasMore = rows.size() > SUMMARY_LIMIT;
        return new CostSummaryResult(
                required.tenantId(), value.occurredFrom(), value.occurredTo(), value.groupBy(),
                hasMore ? rows.subList(0, SUMMARY_LIMIT) : rows, SUMMARY_LIMIT, hasMore);
    }

    private void appendTargetedScope(
            OperatingCostQuerySql.Query query, ScopeContext scope) {
        if (scope.type() == ScopeContext.Type.FARM && scope.resourceId().isPresent()) {
            query.add("resolved_farm.id = ?", scope.resourceId().orElseThrow());
        }
    }

    private Optional<BigDecimal> optionalMoney(BigDecimal value) {
        return Optional.ofNullable(value);
    }
}
