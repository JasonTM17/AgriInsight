package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.agriinsight.backend.inventory.domain.WarehouseAssignment;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseAssignmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final ScopeContext SCOPE = ScopeContext.tenant(new TestPrincipal());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("WAREHOUSE_ACCESS"), Optional.of("request-assignment-1"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final WarehouseAssignmentStore store = mock(WarehouseAssignmentStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private WarehouseAssignmentService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.INVENTORY_ASSIGNMENT_MANAGE)).thenReturn(SCOPE);
        service = new WarehouseAssignmentService(permissions, store, auditPublisher);
    }

    @Test
    void grantRequiresActiveTenantTargetsAndPublishesAudit() {
        var command = grantCommand();
        when(store.activeProfileExists(SCOPE, PROFILE_ID)).thenReturn(true);
        when(store.activeWarehouseExists(SCOPE, WAREHOUSE_ID)).thenReturn(true);
        when(store.create(any(), any(WarehouseAssignment.class)))
                .thenReturn(Optional.of(record(0, true)));

        WarehouseAssignmentRecord granted = service.grant(command);

        assertThat(granted.id()).isEqualTo(ASSIGNMENT_ID);
        verify(permissions).requireTenant(Permission.INVENTORY_ASSIGNMENT_MANAGE);
        org.mockito.ArgumentCaptor<TenantAuditEvent> event =
                org.mockito.ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action())
                .isEqualTo(TenantAuditEvent.Action.WAREHOUSE_ASSIGNMENT_GRANTED);
        assertThat(event.getValue().targetType())
                .isEqualTo(TenantAuditEvent.TargetType.WAREHOUSE_ASSIGNMENT);
    }

    @Test
    void missingActiveWarehouseFailsBeforeMutation() {
        when(store.activeProfileExists(SCOPE, PROFILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.grant(grantCommand()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active warehouse");

        verify(store, never()).create(any(), any());
    }

    @Test
    void racingDuplicateGrantReturnsTypedConflict() {
        when(store.activeProfileExists(SCOPE, PROFILE_ID)).thenReturn(true);
        when(store.activeWarehouseExists(SCOPE, WAREHOUSE_ID)).thenReturn(true);
        when(store.create(any(), any(WarehouseAssignment.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(grantCommand()))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessage("Warehouse assignment is already active");

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void staleRevokeReturnsTypedVersionConflict() {
        var command = new WarehouseAssignmentCommands.Revoke(1, AUDIT);
        when(store.findById(SCOPE, ASSIGNMENT_ID)).thenReturn(Optional.of(record(2, true)));

        assertThatThrownBy(() -> service.revoke(ASSIGNMENT_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(1);
                    assertThat(conflict.currentVersion()).isEqualTo(2);
                });

        verify(store, never()).revoke(any(), any(), anyLong());
        verify(auditPublisher, never()).publish(any());
    }

    private WarehouseAssignmentCommands.Grant grantCommand() {
        return new WarehouseAssignmentCommands.Grant(PROFILE_ID, WAREHOUSE_ID, 0, AUDIT);
    }

    private WarehouseAssignmentRecord record(long version, boolean active) {
        return new WarehouseAssignmentRecord(
                ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, WAREHOUSE_ID,
                active ? Optional.empty() : Optional.of(java.time.Instant.now()), version);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
