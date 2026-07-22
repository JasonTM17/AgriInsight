package com.agriinsight.backend.cost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatingCostQueryServiceTest {

    @Test
    void summariesResolveCostReadThroughFarmScope() {
        PermissionEvaluator permissions = mock(PermissionEvaluator.class);
        OperatingCostQueryStore store = mock(OperatingCostQueryStore.class);
        ScopeContext scope = new ScopeContext(
                UUID.randomUUID(), UUID.randomUUID(), ScopeContext.Type.FARM, Optional.empty());
        CostSummaryQuery query = new CostSummaryQuery(
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-02-01T00:00:00Z"),
                CostSummaryGroup.CATEGORY,
                Optional.empty(), Optional.empty(), Optional.empty());
        CostSummaryResult expected = new CostSummaryResult(
                scope.tenantId(), query.occurredFrom(), query.occurredTo(), query.groupBy(),
                List.of(), 500, false);
        when(permissions.requireDomainList(Permission.COST_READ, ScopeContext.Type.FARM))
                .thenReturn(scope);
        when(store.summarize(scope, query)).thenReturn(expected);

        CostSummaryResult result = new OperatingCostQueryService(
                permissions, store).summarize(query);

        assertThat(result).isSameAs(expected);
        verify(store).summarize(scope, query);
    }
}
