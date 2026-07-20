package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FarmReadServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.FARM, Optional.empty());
    private static final ScopeContext ITEM_SCOPE = ScopeContext.domain(
            new TestPrincipal(), ScopeContext.Type.FARM, Optional.of(FARM_ID));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final FarmStore store = mock(FarmStore.class);
    private FarmService service;

    @BeforeEach
    void createService() {
        service = new FarmService(permissions, store, mock(TenantAuditPublisher.class));
    }

    @Test
    void appliesFarmScopeBeforePagingAndMaterialization() {
        FarmQuery query = new FarmQuery(25, 0, Optional.of(true), Optional.empty());
        FarmRecord farm = farm(0);
        FarmPage expected = new FarmPage(List.of(farm), 25, 0, false);
        when(permissions.requireDomainList(Permission.FARM_READ, ScopeContext.Type.FARM))
                .thenReturn(LIST_SCOPE);
        when(store.findAll(LIST_SCOPE, query)).thenReturn(expected);

        assertThat(service.list(query)).isEqualTo(expected);

        verify(store).findAll(LIST_SCOPE, query);
    }

    @Test
    void hiddenOrUnknownFarmReturnsNotFoundAfterScopedLookup() {
        when(permissions.requireDomain(Permission.FARM_READ, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(ITEM_SCOPE);
        when(store.findById(ITEM_SCOPE, FARM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(FARM_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Farm");
    }

    private FarmRecord farm(long version) {
        return new FarmRecord(FARM_ID, TENANT_ID, "NORTH", "North Farm", true, version);
    }

    private record TestPrincipal() implements com.agriinsight.backend.shared.security.TenantPrincipal {

        @Override
        public UUID profileId() {
            return PROFILE_ID;
        }

        @Override
        public UUID tenantId() {
            return TENANT_ID;
        }

        @Override
        public String getName() {
            return PROFILE_ID.toString();
        }
    }
}
