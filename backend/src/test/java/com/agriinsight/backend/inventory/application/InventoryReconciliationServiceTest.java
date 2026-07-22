package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReconciliationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final ScopeContext SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.WAREHOUSE, Optional.empty());

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final InventoryReconciliationStore store = mock(InventoryReconciliationStore.class);
    private final InventoryReconciliationService service =
            new InventoryReconciliationService(permissions, store);

    @Test
    void consistentProjectionReturnsScopedReport() {
        var report = new InventoryReconciliationReport(7, 0, 3, 0, 1, 0);
        when(permissions.requireDomainList(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE)).thenReturn(SCOPE);
        when(store.reconcile(SCOPE)).thenReturn(report);

        assertThat(service.verify()).isEqualTo(report);
        verify(store).reconcile(SCOPE);
    }

    @Test
    void driftFailsWithoutRepairingProjection() {
        var report = new InventoryReconciliationReport(7, 1, 3, 1, 1, 1);
        when(permissions.requireDomainList(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE)).thenReturn(SCOPE);
        when(store.reconcile(SCOPE)).thenReturn(report);

        assertThatThrownBy(service::verify)
                .isInstanceOfSatisfying(InventoryProjectionDriftException.class,
                        failure -> assertThat(failure.report()).isEqualTo(report));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
