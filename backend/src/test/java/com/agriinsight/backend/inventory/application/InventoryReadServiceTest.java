package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReadServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final ScopeContext SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.WAREHOUSE, Optional.empty());

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final InventoryReadStore store = mock(InventoryReadStore.class);
    private final InventoryReadService service = new InventoryReadService(permissions, store);

    @Test
    void balanceReadUsesWarehouseListScope() {
        var query = new StockBalanceQuery(
                50, 0, Optional.empty(), Optional.empty(), Optional.empty());
        when(permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE)).thenReturn(SCOPE);
        when(store.findBalances(SCOPE, query))
                .thenReturn(new StockBalancePage(java.util.List.of(), 50, 0, false));

        service.listBalances(query);

        verify(store).findBalances(SCOPE, query);
    }

    @Test
    void hiddenTransactionReturnsSafeNotFound() {
        when(permissions.requireDomainList(
                Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE)).thenReturn(SCOPE);

        assertThatThrownBy(() -> service.getTransaction(TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Inventory transaction was not found");

        verify(store).findTransaction(SCOPE, TRANSACTION_ID);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
