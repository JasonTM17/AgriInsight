package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_SCOPE;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.LIST_SCOPE;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.activity;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.updateCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.Activity;
import com.agriinsight.backend.operations.domain.ActivityStatus;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityMutationServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final ActivityStore store = mock(ActivityStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private ActivityService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomainList(Permission.ACTIVITY_MANAGE, ScopeContext.Type.ACTIVITY))
                .thenReturn(LIST_SCOPE);
        when(permissions.requireDomain(Permission.ACTIVITY_MANAGE, ScopeContext.Type.FARM, FARM_ID))
                .thenReturn(FARM_SCOPE);
        when(store.farmVisible(FARM_SCOPE, FARM_ID)).thenReturn(true);
        service = new ActivityService(permissions, store, auditPublisher);
    }

    @Test
    void createsOnlyWithAvailableLiveParentsAndPublishesAudit() {
        when(store.liveParentsAvailable(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.create(any(), any(Activity.class))).thenAnswer(invocation -> {
            Activity created = invocation.getArgument(1);
            return Optional.of(activity(0, ActivityStatus.PLANNED));
        });

        ActivityRecord created = service.create(createCommand());

        assertThat(created.status()).isEqualTo(ActivityStatus.PLANNED);
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.ACTIVITY_CREATED);
        assertThat(event.getValue().targetType()).isEqualTo(TenantAuditEvent.TargetType.ACTIVITY);
    }

    @Test
    void terminalActivitiesRejectMetadataAndReverseTransitions() {
        when(store.findById(LIST_SCOPE, ACTIVITY_ID))
                .thenReturn(Optional.of(activity(4, ActivityStatus.COMPLETED)));

        assertThatThrownBy(() -> service.update(ACTIVITY_ID, updateCommand(4)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> service.transition(
                ACTIVITY_ID,
                new ActivityCommands.Transition(
                        ActivityStatus.STARTED, Instant.parse("2027-01-01T02:30:00Z"), 4, AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("not allowed");
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void plannedActivityStartsWithOptimisticVersion() {
        ActivityRecord planned = activity(0, ActivityStatus.PLANNED);
        ActivityRecord started = activity(1, ActivityStatus.STARTED);
        when(store.findById(LIST_SCOPE, ACTIVITY_ID)).thenReturn(Optional.of(planned));
        when(store.liveParentsAvailable(any(), any(), any(), any(), any())).thenReturn(true);
        when(store.transition(
                FARM_SCOPE, ACTIVITY_ID, 0, ActivityStatus.PLANNED, ActivityStatus.STARTED,
                Instant.parse("2027-01-01T01:00:00Z"))).thenReturn(Optional.of(started));

        assertThat(service.transition(
                ACTIVITY_ID,
                new ActivityCommands.Transition(
                        ActivityStatus.STARTED, Instant.parse("2027-01-01T01:00:00Z"), 0, AUDIT)))
                .isEqualTo(started);

        verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }
}
