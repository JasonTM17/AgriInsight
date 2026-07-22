package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.PROFILE_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.TENANT_ID;
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
import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityLogServiceTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("37000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EMPLOYEE_ID = UUID.fromString("37000000-0000-0000-0000-000000000002");
    private static final UUID LOG_ID = UUID.fromString("62000000-0000-0000-0000-000000000001");
    private static final ScopeContext SCOPE = ScopeContext.domain(
            ActivityApplicationTestFixtures.PRINCIPAL,
            ScopeContext.Type.ACTIVITY,
            Optional.of(ACTIVITY_ID));

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final ActivityLogStore store = mock(ActivityLogStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private ActivityLogService service;

    @BeforeEach
    void createService() {
        when(permissions.requireDomain(
                Permission.ACTIVITY_LOG_APPEND, ScopeContext.Type.ACTIVITY, ACTIVITY_ID))
                .thenReturn(SCOPE);
        service = new ActivityLogService(permissions, store, auditPublisher);
    }

    @Test
    void managerAppendsForAnAssignedEmployeeAndPublishesAudit() {
        when(store.resolveAccess(SCOPE, ACTIVITY_ID))
                .thenReturn(Optional.of(new ActivityLogAccess(FARM_ID, true, Optional.empty())));
        when(store.append(any(), any(ActivityLog.class))).thenAnswer(invocation ->
                Optional.of(record(invocation.getArgument(1))));

        ActivityLogRecord created = service.append(ACTIVITY_ID, append(EMPLOYEE_ID));

        assertThat(created.employeeId()).isEqualTo(EMPLOYEE_ID);
        ArgumentCaptor<TenantAuditEvent> event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher).publish(event.capture());
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.ACTIVITY_LOG_APPENDED);
    }

    @Test
    void workerCannotSpoofAnotherAssignedEmployee() {
        when(store.resolveAccess(SCOPE, ACTIVITY_ID)).thenReturn(Optional.of(
                new ActivityLogAccess(FARM_ID, false, Optional.of(EMPLOYEE_ID))));

        assertThatThrownBy(() -> service.append(ACTIVITY_ID, append(OTHER_EMPLOYEE_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("assignment");

        verify(store, never()).append(any(), any());
    }

    @Test
    void workerCanCorrectOnlyOwnAuthoredLog() {
        when(store.resolveAccess(SCOPE, ACTIVITY_ID)).thenReturn(Optional.of(
                new ActivityLogAccess(FARM_ID, false, Optional.of(EMPLOYEE_ID))));
        ActivityLogRecord otherAuthor = new ActivityLogRecord(
                LOG_ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, UUID.randomUUID(),
                Instant.parse("2027-01-01T01:00:00Z"), Optional.of("Original"),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0);
        when(store.findById(SCOPE, ACTIVITY_ID, LOG_ID)).thenReturn(Optional.of(otherAuthor));

        assertThatThrownBy(() -> service.correct(ACTIVITY_ID, LOG_ID, correction()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Activity log");

        verify(store, never()).append(any(), any());
    }

    private ActivityLogCommands.Append append(UUID employeeId) {
        return new ActivityLogCommands.Append(
                employeeId, Instant.parse("2027-01-01T01:00:00Z"), Optional.of("Harvested"),
                Optional.of(new BigDecimal("100")), Optional.of(ActivityLogUnit.KG),
                Optional.of("https://evidence.example/log-1"), AUDIT);
    }

    private ActivityLogCommands.Correct correction() {
        return new ActivityLogCommands.Correct(
                ActivityLogCorrectionKind.REPLACE, Instant.parse("2027-01-01T01:05:00Z"),
                Optional.of("Corrected"), Optional.of(new BigDecimal("101")),
                Optional.of(ActivityLogUnit.KG), Optional.empty(), "Quantity corrected", AUDIT);
    }

    private ActivityLogRecord record(ActivityLog log) {
        return new ActivityLogRecord(
                log.id(), log.tenantId(), log.activityId(), log.employeeId(), log.authorProfileId(),
                log.occurredAt(), log.notes(), log.quantity(), log.unit(), log.evidenceUri(),
                log.correctsLogId(), log.correctionKind(), log.correctionReason(), 0);
    }
}
