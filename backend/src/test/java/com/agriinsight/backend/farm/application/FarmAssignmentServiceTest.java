package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.FarmAssignment;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FarmAssignmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FARM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    private static final ScopeContext SCOPE = new ScopeContext(
            TENANT_ID, ACTOR_ID, ScopeContext.Type.TENANT, Optional.empty());
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("FARM_ACCESS_CHANGE"), Optional.of("farm-assignment-request-1"));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final FarmAssignmentStore store = mock(FarmAssignmentStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private FarmAssignmentService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.FARM_ASSIGNMENT_MANAGE)).thenReturn(SCOPE);
        when(store.activeProfileExists(SCOPE, PROFILE_ID)).thenReturn(true);
        when(store.activeFarmExists(SCOPE, FARM_ID)).thenReturn(true);
        service = new FarmAssignmentService(permissions, store, auditPublisher);
    }

    @Test
    void grantRequiresActiveTenantTargetsAndAuditsVersionZeroAssignment() {
        FarmAssignmentRecord created = assignment(true, 0);
        when(store.create(any(), any(FarmAssignment.class))).thenReturn(created);

        assertThat(service.grant(grant(0))).isEqualTo(created);

        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action())
                .isEqualTo(TenantAuditEvent.Action.FARM_ASSIGNMENT_GRANTED);
        assertThat(event.getValue().targetType())
                .isEqualTo(TenantAuditEvent.TargetType.FARM_ASSIGNMENT);
    }

    @Test
    void missingOrInactiveProfileIsHiddenBeforeFarmLookupAndMutation() {
        when(store.activeProfileExists(SCOPE, PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.grant(grant(0)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active tenant user");

        verify(store, never()).activeFarmExists(any(), any());
        verify(store, never()).create(any(), any());
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void duplicateOrNonzeroGrantCannotRewriteAssignmentHistory() {
        assertThatThrownBy(() -> service.grant(grant(2)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("version 0");

        when(store.findActive(SCOPE, PROFILE_ID, FARM_ID))
                .thenReturn(Optional.of(assignment(true, 0)));
        assertThatThrownBy(() -> service.grant(grant(0)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("already active");

        verify(store, never()).create(any(), any());
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void preclaimValidationAllowsExecutorToReplayAnExistingGrant() {
        when(store.findActive(SCOPE, PROFILE_ID, FARM_ID))
                .thenReturn(Optional.of(assignment(true, 0)));

        assertThatCode(() -> service.requireGrantTargets(grant(0)))
                .doesNotThrowAnyException();

        verify(store, never()).findActive(any(), any(), any());
        verify(store, never()).create(any(), any());
    }

    @Test
    void revokeUsesOptimisticVersionAndPublishesAudit() {
        FarmAssignmentRecord active = assignment(true, 3);
        FarmAssignmentRecord revoked = assignment(false, 4);
        when(store.findById(SCOPE, ASSIGNMENT_ID)).thenReturn(Optional.of(active));
        when(store.revoke(SCOPE, ASSIGNMENT_ID, 3)).thenReturn(Optional.of(revoked));

        assertThat(service.revoke(ASSIGNMENT_ID, revoke(3))).isEqualTo(revoked);

        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action())
                .isEqualTo(TenantAuditEvent.Action.FARM_ASSIGNMENT_REVOKED);
    }

    @Test
    void staleRevokeReturnsObservedCurrentVersionWithoutWriting() {
        when(store.findById(SCOPE, ASSIGNMENT_ID)).thenReturn(Optional.of(assignment(true, 4)));

        assertThatThrownBy(() -> service.revoke(ASSIGNMENT_ID, revoke(3)))
                .isInstanceOfSatisfying(VersionConflictException.class, exception -> {
                    assertThat(exception.expectedVersion()).isEqualTo(3);
                    assertThat(exception.currentVersion()).isEqualTo(4);
                });

        verify(store, never()).revoke(any(), any(), anyLong());
        verifyNoInteractions(auditPublisher);
    }

    private FarmAssignmentCommands.Grant grant(long version) {
        return new FarmAssignmentCommands.Grant(PROFILE_ID, FARM_ID, version, AUDIT);
    }

    private FarmAssignmentCommands.Revoke revoke(long version) {
        return new FarmAssignmentCommands.Revoke(version, AUDIT);
    }

    private FarmAssignmentRecord assignment(boolean active, long version) {
        return new FarmAssignmentRecord(
                ASSIGNMENT_ID,
                TENANT_ID,
                PROFILE_ID,
                FARM_ID,
                active ? Optional.empty() : Optional.of(Instant.EPOCH),
                version);
    }
}
