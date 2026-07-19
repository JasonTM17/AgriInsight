package com.agriinsight.backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantContextStateTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final TenantContextState state = new TenantContextState();

    @AfterEach
    void clearThreadState() {
        if (state.currentTenantId().isPresent()) {
            state.unbind();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void bindsOnlyInsideATransactionAndRequiresAnExactTenant() {
        assertThatThrownBy(() -> state.bind(TENANT_A))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Tenant context requires an active transaction");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        state.bind(TENANT_A);

        assertThat(state.currentTenantId()).contains(TENANT_A);
        state.requireBound(TENANT_A);
        assertThatThrownBy(() -> state.requireBound(TENANT_B))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Matching tenant context is required");
        assertThatThrownBy(() -> state.bind(TENANT_A))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Tenant context is already bound");
    }
}
