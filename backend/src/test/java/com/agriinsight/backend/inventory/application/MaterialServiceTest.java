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
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.inventory.domain.Material;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    private static final ScopeContext CATALOG_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.WAREHOUSE, Optional.empty());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("MATERIAL_CHANGE"), Optional.of("request-material-01"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final MaterialStore store = mock(MaterialStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private MaterialService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.INVENTORY_MANAGE)).thenReturn(TENANT_SCOPE);
        service = new MaterialService(permissions, store, auditPublisher);
    }

    @Test
    void listUsesWarehouseCatalogScopeBeforePaging() {
        var query = new MaterialQuery(50, 0, Optional.empty(), Optional.empty());
        var page = new MaterialPage(java.util.List.of(), 50, 0, false);
        when(permissions.requireDomainList(Permission.INVENTORY_READ, ScopeContext.Type.WAREHOUSE))
                .thenReturn(CATALOG_SCOPE);
        when(store.findAll(CATALOG_SCOPE, query)).thenReturn(page);

        assertThat(service.list(query)).isSameAs(page);

        verify(store).findAll(CATALOG_SCOPE, query);
    }

    @Test
    void createCanonicalizesDataAndPublishesMaterialAudit() {
        when(store.create(any(), any(Material.class))).thenAnswer(invocation -> {
            Material material = invocation.getArgument(1);
            return new MaterialRecord(
                    material.id(), material.tenantId(), material.code(), material.displayName(),
                    material.baseUnit(), material.minimumStockQuantity(), true, 0);
        });

        MaterialRecord created = service.create(new MaterialCommands.Create(
                " fert.n ", " Fertilizer N ", CanonicalUnit.KG,
                Optional.of(new BigDecimal("15.0000")), AUDIT));

        assertThat(created.code()).isEqualTo("FERT.N");
        assertThat(created.minimumStockQuantity()).contains(new BigDecimal("15"));
        org.mockito.ArgumentCaptor<TenantAuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.MATERIAL_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.MATERIAL);
    }

    @Test
    void staleTenantMaterialUpdateReturnsTypedConflict() {
        var command = new MaterialCommands.Update(
                Optional.empty(), Optional.of("Updated Material"), Optional.empty(),
                Optional.empty(), 2, AUDIT);
        when(store.findById(TENANT_SCOPE, MATERIAL_ID)).thenReturn(Optional.of(record(3, true)));
        when(store.update(TENANT_SCOPE, MATERIAL_ID, 2, command)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(MATERIAL_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void inventoryReferencesBlockTenantScopedDeactivation() {
        var command = new MaterialCommands.Lifecycle(4, AUDIT);
        when(store.findById(TENANT_SCOPE, MATERIAL_ID)).thenReturn(Optional.of(record(4, true)));
        when(store.updateActive(TENANT_SCOPE, MATERIAL_ID, 4, false)).thenReturn(Optional.empty());
        when(store.hasReferences(TENANT_SCOPE, MATERIAL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(MATERIAL_ID, command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("Material has inventory references");

        verify(permissions).requireTenant(Permission.INVENTORY_MANAGE);
        verify(auditPublisher, never()).publish(any());
    }

    private MaterialRecord record(long version, boolean active) {
        return new MaterialRecord(
                MATERIAL_ID, TENANT_ID, "FERT.N", "Fertilizer N", CanonicalUnit.KG,
                Optional.of(new BigDecimal("15")), active, version);
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
