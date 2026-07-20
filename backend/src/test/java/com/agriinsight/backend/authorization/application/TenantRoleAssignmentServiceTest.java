package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantRoleAssignmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("22000000-0000-0000-0000-000000000004");
    private static final ScopeContext SCOPE = new ScopeContext(
            TENANT_ID,
            ACTOR_ID,
            ScopeContext.Type.TENANT,
            Optional.empty());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("ACCESS_APPROVED"),
            Optional.of("role-request-01"));

    private final PermissionEvaluator permissionEvaluator = mock(PermissionEvaluator.class);
    private final TenantRoleAssignmentStore store = mock(TenantRoleAssignmentStore.class);
    private final TenantAdministratorGuard administratorGuard = mock(TenantAdministratorGuard.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private TenantRoleAssignmentService service;

    @BeforeEach
    void createService() {
        when(permissionEvaluator.requireTenant(Permission.IDENTITY_ROLE_MANAGE)).thenReturn(SCOPE);
        when(store.profileExists(SCOPE, PROFILE_ID)).thenReturn(true);
        service = new TenantRoleAssignmentService(
                permissionEvaluator, store, administratorGuard, auditPublisher);
    }

    @Test
    void grantsANewFixedRoleAtVersionZeroAndAuditsTheAssignment() {
        when(store.find(SCOPE, PROFILE_ID, Role.DATA_ANALYST)).thenReturn(Optional.empty());
        when(store.create(eq(SCOPE), any(UUID.class), eq(PROFILE_ID), eq(Role.DATA_ANALYST)))
                .thenReturn(Optional.of(assignment(Role.DATA_ANALYST, true, 0)));

        TenantRoleAssignment granted = service.grant(
                PROFILE_ID,
                new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 0, AUDIT));

        assertThat(granted).isEqualTo(assignment(Role.DATA_ANALYST, true, 0));
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.ROLE_GRANTED);
        assertThat(event.getValue().targetReference()).contains("DATA_ANALYST");
    }

    @Test
    void regrantRequiresTheCurrentRevokedVersion() {
        TenantRoleAssignment revoked = assignment(Role.DATA_ANALYST, false, 2);
        when(store.find(SCOPE, PROFILE_ID, Role.DATA_ANALYST)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.grant(
                PROFILE_ID,
                new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 1, AUDIT)))
                .isInstanceOfSatisfying(VersionConflictException.class, exception -> {
                    assertThat(exception.expectedVersion()).isEqualTo(1);
                    assertThat(exception.currentVersion()).isEqualTo(2);
                });

        verify(store, never()).reactivate(any(), any(), any(), anyLong());
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void revokingTenantAdminGuardsTheAdminPathBeforeUpdatingAndAuditing() {
        TenantRoleAssignment active = assignment(Role.TENANT_ADMIN, true, 4);
        TenantRoleAssignment revoked = assignment(Role.TENANT_ADMIN, false, 5);
        when(store.find(SCOPE, PROFILE_ID, Role.TENANT_ADMIN)).thenReturn(Optional.of(active));
        when(store.revoke(SCOPE, PROFILE_ID, Role.TENANT_ADMIN, 4)).thenReturn(Optional.of(revoked));

        assertThat(service.revoke(
                PROFILE_ID,
                Role.TENANT_ADMIN,
                new TenantRoleAssignmentCommands.Revoke(4, AUDIT)))
                .isEqualTo(revoked);

        var order = inOrder(administratorGuard, store, auditPublisher);
        order.verify(administratorGuard).assertPathRemains(SCOPE, PROFILE_ID);
        order.verify(store).revoke(SCOPE, PROFILE_ID, Role.TENANT_ADMIN, 4);
        order.verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }

    @Test
    void missingTenantUserIsHiddenAndProducesNoAudit() {
        when(store.profileExists(SCOPE, PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.grant(
                PROFILE_ID,
                new TenantRoleAssignmentCommands.Grant(Role.EXECUTIVE, 0, AUDIT)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant user");

        verify(store, never()).find(any(), any(), any());
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void concurrentRoleMutationReturnsTheObservedCurrentVersion() {
        TenantRoleAssignment before = assignment(Role.EXECUTIVE, true, 3);
        TenantRoleAssignment concurrent = assignment(Role.EXECUTIVE, false, 4);
        when(store.find(SCOPE, PROFILE_ID, Role.EXECUTIVE))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(concurrent));
        when(store.revoke(SCOPE, PROFILE_ID, Role.EXECUTIVE, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(
                PROFILE_ID,
                Role.EXECUTIVE,
                new TenantRoleAssignmentCommands.Revoke(3, AUDIT)))
                .isInstanceOfSatisfying(VersionConflictException.class, exception ->
                        assertThat(exception.currentVersion()).isEqualTo(4));
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void duplicateActiveGrantReturnsAStateConflictWithoutWriting() {
        when(store.find(SCOPE, PROFILE_ID, Role.EXECUTIVE))
                .thenReturn(Optional.of(assignment(Role.EXECUTIVE, true, 2)));

        assertThatThrownBy(() -> service.grant(
                PROFILE_ID,
                new TenantRoleAssignmentCommands.Grant(Role.EXECUTIVE, 2, AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("already active");

        verify(store, never()).reactivate(any(), any(), any(), anyLong());
        verifyNoInteractions(auditPublisher);
    }

    private TenantRoleAssignment assignment(Role role, boolean active, long version) {
        return new TenantRoleAssignment(
                ASSIGNMENT_ID,
                TENANT_ID,
                PROFILE_ID,
                role,
                active,
                version);
    }
}
