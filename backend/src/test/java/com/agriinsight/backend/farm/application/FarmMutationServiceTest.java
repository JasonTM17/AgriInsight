package com.agriinsight.backend.farm.application;

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
import com.agriinsight.backend.farm.domain.Farm;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FarmMutationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    private static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("MASTER_DATA_CHANGE"), Optional.of("request-02"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final FarmStore store = mock(FarmStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private FarmService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.FARM_MANAGE)).thenReturn(TENANT_SCOPE);
        when(permissions.requireDomain(Permission.FARM_MANAGE, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(FARM_SCOPE);
        service = new FarmService(permissions, store, auditPublisher);
    }

    @Test
    void createsCanonicalFarmAndPublishesACommitBoundAudit() {
        when(store.create(any(), any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(1);
            return new FarmRecord(
                    farm.id(), farm.tenantId(), farm.code(), farm.displayName(), true, 0);
        });

        FarmRecord created = service.create(new FarmCommands.Create(
                " north ", " North Farm ", AUDIT));

        assertThat(created.code()).isEqualTo("NORTH");
        assertThat(created.displayName()).isEqualTo("North Farm");
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.FARM_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.FARM);
    }

    @Test
    void stalePatchReturnsTypedConflictWithoutSuccessAudit() {
        FarmCommands.Update command = new FarmCommands.Update(
                Optional.empty(), Optional.of("Updated Farm"), 2, AUDIT);
        when(store.update(
                FARM_SCOPE, FARM_ID, 2, Optional.empty(), Optional.of("Updated Farm")))
                .thenReturn(Optional.empty());
        when(store.findById(FARM_SCOPE, FARM_ID)).thenReturn(Optional.of(farm(3, true)));

        assertThatThrownBy(() -> service.update(FARM_ID, command))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });

        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void duplicateLifecycleStateIsAConflictAndLifecycleRequiresTenantScope() {
        FarmCommands.Lifecycle command = new FarmCommands.Lifecycle(4, AUDIT);
        when(store.updateActive(TENANT_SCOPE, FARM_ID, 4, false)).thenReturn(Optional.empty());
        when(store.findById(TENANT_SCOPE, FARM_ID)).thenReturn(Optional.of(farm(4, false)));

        assertThatThrownBy(() -> service.deactivate(FARM_ID, command))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("already inactive");

        verify(permissions).requireTenant(Permission.FARM_MANAGE);
        verify(auditPublisher, never()).publish(any());
    }

    private FarmRecord farm(long version, boolean active) {
        return new FarmRecord(FARM_ID, TENANT_ID, "NORTH", "North Farm", active, version);
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
