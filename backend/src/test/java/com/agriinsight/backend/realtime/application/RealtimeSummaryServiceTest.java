package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeSummaryServiceTest {

    @Test
    void resolvesRealtimeReadThroughTenantScope() {
        PermissionEvaluator permissions = mock(PermissionEvaluator.class);
        RealtimeSummaryStore store = mock(RealtimeSummaryStore.class);
        ScopeContext scope = new ScopeContext(
                UUID.randomUUID(), UUID.randomUUID(), ScopeContext.Type.TENANT, Optional.empty());
        RealtimeSummary expected = new RealtimeSummary(
                scope.tenantId(), 0, Optional.empty(), Optional.empty(), 0, List.of(), 100, false);
        when(permissions.requireTenant(Permission.REALTIME_READ)).thenReturn(scope);
        when(store.summarize(scope)).thenReturn(expected);

        RealtimeSummary result = new RealtimeSummaryService(permissions, store).summarize();

        assertThat(result).isSameAs(expected);
        verify(store).summarize(scope);
    }
}
