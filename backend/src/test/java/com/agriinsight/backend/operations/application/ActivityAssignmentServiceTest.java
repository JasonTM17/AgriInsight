package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_SCOPE;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.TENANT_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.activity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.operations.domain.ActivityAssignment;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityAssignmentServiceTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("37000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("39000000-0000-0000-0000-000000000001");

    private final ActivityService activities = mock(ActivityService.class);
    private final ActivityAssignmentStore store = mock(ActivityAssignmentStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private ActivityAssignmentService service;

    @BeforeEach
    void createService() {
        when(activities.getForManagement(ACTIVITY_ID))
                .thenReturn(activity(0, ActivityStatus.PLANNED));
        when(activities.requireFarmManagement(any())).thenReturn(FARM_SCOPE);
        when(activities.requireManagementScope()).thenReturn(LIST_SCOPE);
        service = new ActivityAssignmentService(activities, store, auditPublisher);
    }

    @Test
    void grantRequiresActiveEmployeeAndPublishesAudit() {
        when(store.activeEmployeeExists(FARM_SCOPE, EMPLOYEE_ID)).thenReturn(true);
        when(store.findActive(FARM_SCOPE, ACTIVITY_ID, EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(store.create(any(), any(ActivityAssignment.class)))
                .thenReturn(Optional.of(assignment(0, Optional.empty())));

        ActivityAssignmentRecord granted = service.grant(ACTIVITY_ID, grant(0));

        assertThat(granted.active()).isTrue();
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action())
                .isEqualTo(TenantAuditEvent.Action.ACTIVITY_ASSIGNMENT_GRANTED);
        assertThat(event.getValue().targetType())
                .isEqualTo(TenantAuditEvent.TargetType.ACTIVITY_ASSIGNMENT);
    }

    @Test
    void terminalActivityCannotReceiveAssignments() {
        when(activities.getForManagement(ACTIVITY_ID))
                .thenReturn(activity(4, ActivityStatus.COMPLETED));
        when(store.activeEmployeeExists(FARM_SCOPE, EMPLOYEE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.grant(ACTIVITY_ID, grant(0)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("Terminal");

        verify(store, never()).create(any(), any());
    }

    @Test
    void staleRevokeReturnsVersionConflictWithoutAudit() {
        when(store.findById(LIST_SCOPE, ASSIGNMENT_ID))
                .thenReturn(Optional.of(assignment(2, Optional.empty())));

        assertThatThrownBy(() -> service.revoke(
                ASSIGNMENT_ID, new ActivityAssignmentCommands.Revoke(1, AUDIT)))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(1);
                    assertThat(conflict.currentVersion()).isEqualTo(2);
                });

        verify(store, never()).revoke(any(), any(), anyLong());
        verify(auditPublisher, never()).publish(any());
    }

    private ActivityAssignmentCommands.Grant grant(long version) {
        return new ActivityAssignmentCommands.Grant(EMPLOYEE_ID, version, AUDIT);
    }

    private ActivityAssignmentRecord assignment(long version, Optional<Instant> revokedAt) {
        return new ActivityAssignmentRecord(
                ASSIGNMENT_ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, revokedAt, version);
    }
}
