package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.inventory.domain.Warehouse;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    private static final ScopeContext WAREHOUSE_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.of(WAREHOUSE_ID));
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("WAREHOUSE_CHANGE"), Optional.of("request-warehouse-01"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final WarehouseStore store = mock(WarehouseStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private WarehouseService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.INVENTORY_MANAGE)).thenReturn(TENANT_SCOPE);
        when(permissions.requireDomain(
                Permission.INVENTORY_MANAGE, ScopeContext.Type.WAREHOUSE, WAREHOUSE_ID))
                .thenReturn(WAREHOUSE_SCOPE);
        service = new WarehouseService(permissions, store, auditPublisher);
    }

    @Test
    void listUsesWarehouseScopeBeforePaging() {
        var query = new WarehouseQuery(50, 0, Optional.empty(), Optional.empty());
        var page = new WarehousePage(java.util.List.of(), 50, 0, false);
        when(permissions.requireDomainList(Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE))
                .thenReturn(WAREHOUSE_SCOPE);
        when(store.findAll(WAREHOUSE_SCOPE, query)).thenReturn(page);

        assertThat(service.list(query)).isSameAs(page);

        verify(store).findAll(WAREHOUSE_SCOPE, query);
    }

    @Test
    void createCanonicalizesDataAndPublishesWarehouseAudit() {
        when(store.create(any(), any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(1);
            return new WarehouseRecord(
                    warehouse.id(), warehouse.tenantId(), warehouse.code(), warehouse.displayName(),
                    warehouse.locationText(), true, 0);
        });

        WarehouseRecord created = service.create(new WarehouseCommands.Create(
                " wh-main ", " Kho trung tâm ", Optional.of(" Đắk Lắk "), AUDIT));

        assertThat(created.code()).isEqualTo("WH-MAIN");
        assertThat(created.locationText()).contains("Đắk Lắk");
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.WAREHOUSE_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.WAREHOUSE);
    }

    @Test
    void staleAssignedWarehouseUpdateReturnsTypedConflict() {
        var command = new WarehouseCommands.Update(
                Optional.empty(), Optional.of("Kho mới"), Optional.empty(), 2, AUDIT);
        when(store.update(
                WAREHOUSE_SCOPE, WAREHOUSE_ID, 2,
                Optional.empty(), Optional.of("Kho mới"), Optional.empty()))
                .thenReturn(Optional.empty());
        when(store.findById(WAREHOUSE_SCOPE, WAREHOUSE_ID)).thenReturn(Optional.of(record(3, true)));

        assertThatThrownBy(() -> service.update(WAREHOUSE_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void activeStockBlocksTenantScopedDeactivation() {
        var command = new WarehouseCommands.Lifecycle(4, AUDIT);
        when(store.updateActive(TENANT_SCOPE, WAREHOUSE_ID, 4, false)).thenReturn(Optional.empty());
        when(store.findById(TENANT_SCOPE, WAREHOUSE_ID)).thenReturn(Optional.of(record(4, true)));
        when(store.hasDeactivationBlockers(TENANT_SCOPE, WAREHOUSE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(WAREHOUSE_ID, command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("Warehouse has active dependents or stock");

        verify(permissions).requireTenant(Permission.INVENTORY_MANAGE);
        verify(auditPublisher, never()).publish(any());
    }

    private WarehouseRecord record(long version, boolean active) {
        return new WarehouseRecord(
                WAREHOUSE_ID, TENANT_ID, "WH-MAIN", "Kho trung tâm",
                Optional.of("Đắk Lắk"), active, version);
    }

    private record TestPrincipal() implements TenantPrincipal {

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
