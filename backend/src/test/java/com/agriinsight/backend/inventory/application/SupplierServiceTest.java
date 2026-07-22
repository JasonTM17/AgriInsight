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
import com.agriinsight.backend.inventory.domain.Supplier;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SUPPLIER_ID = UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    private static final ScopeContext CATALOG_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("SUPPLIER_CHANGE"), Optional.of("request-supplier-01"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final SupplierStore store = mock(SupplierStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private SupplierService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.INVENTORY_MANAGE)).thenReturn(TENANT_SCOPE);
        service = new SupplierService(permissions, store, auditPublisher);
    }

    @Test
    void listUsesWarehouseCatalogScopeBeforePaging() {
        var query = new SupplierQuery(50, 0, Optional.empty(), Optional.empty());
        var page = new SupplierPage(java.util.List.of(), 50, 0, false);
        when(permissions.requireDomainList(Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE))
                .thenReturn(CATALOG_SCOPE);
        when(store.findAll(CATALOG_SCOPE, query)).thenReturn(page);

        assertThat(service.list(query)).isSameAs(page);

        verify(store).findAll(CATALOG_SCOPE, query);
    }

    @Test
    void createCanonicalizesDataAndPublishesSafeSupplierAudit() {
        when(store.create(any(), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(1);
            return new SupplierRecord(
                    supplier.id(), supplier.tenantId(), supplier.code(), supplier.displayName(),
                    true, 0);
        });

        SupplierRecord created = service.create(new SupplierCommands.Create(
                " supplier.n ", " Supplier North ", AUDIT));

        assertThat(created.code()).isEqualTo("SUPPLIER.N");
        org.mockito.ArgumentCaptor<TenantAuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.SUPPLIER_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.SUPPLIER);
    }

    @Test
    void staleTenantSupplierUpdateReturnsTypedConflict() {
        var command = new SupplierCommands.Update(
                Optional.empty(), Optional.of("Updated Supplier"), 2, AUDIT);
        when(store.findById(TENANT_SCOPE, SUPPLIER_ID)).thenReturn(Optional.of(record(3, true)));
        when(store.update(TENANT_SCOPE, SUPPLIER_ID, 2, command)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(SUPPLIER_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void inventoryReferencesBlockTenantScopedDeactivation() {
        var command = new SupplierCommands.Lifecycle(4, AUDIT);
        when(store.findById(TENANT_SCOPE, SUPPLIER_ID)).thenReturn(Optional.of(record(4, true)));
        when(store.updateActive(TENANT_SCOPE, SUPPLIER_ID, 4, false)).thenReturn(Optional.empty());
        when(store.hasReferences(TENANT_SCOPE, SUPPLIER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(SUPPLIER_ID, command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("Supplier has inventory references");

        verify(permissions).requireTenant(Permission.INVENTORY_MANAGE);
        verify(auditPublisher, never()).publish(any());
    }

    private SupplierRecord record(long version, boolean active) {
        return new SupplierRecord(
                SUPPLIER_ID, TENANT_ID, "SUPPLIER.N", "Supplier North", active, version);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
