package com.agriinsight.backend.operations.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityCollectionReadServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ACTIVITY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EMPLOYEE_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID LOG_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Mock private PermissionEvaluator permissions;
    @Mock private ActivityLogStore logs;
    @Mock private ActivityLogReadStore logReads;
    @Mock private ActivityAssignmentReadStore assignmentReads;

    private ScopeContext scope;
    private ActivityReadPageQuery query;

    @BeforeEach
    void setUp() {
        scope = new ScopeContext(
                TENANT_ID,
                PROFILE_ID,
                ScopeContext.Type.ACTIVITY,
                Optional.of(ACTIVITY_ID));
        query = new ActivityReadPageQuery(25, 0);
        when(permissions.requireDomain(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY, ACTIVITY_ID))
                .thenReturn(scope);
    }

    @Test
    void fieldWorkerAssignmentReadIsRestrictedToLinkedEmployee() {
        ActivityLogAccess access = new ActivityLogAccess(
                UUID.randomUUID(), false, Optional.of(EMPLOYEE_ID));
        ActivityAssignmentPage page = new ActivityAssignmentPage(List.of(), 25, 0, false);
        when(logs.resolveAccess(scope, ACTIVITY_ID)).thenReturn(Optional.of(access));
        when(assignmentReads.findAll(
                scope, ACTIVITY_ID, Optional.of(EMPLOYEE_ID), query)).thenReturn(page);

        var service = new ActivityAssignmentReadService(
                permissions, logs, assignmentReads);
        service.list(ACTIVITY_ID, query);

        verify(assignmentReads).findAll(
                scope, ACTIVITY_ID, Optional.of(EMPLOYEE_ID), query);
        verify(permissions).requireDomain(
                Permission.ACTIVITY_READ, ScopeContext.Type.ACTIVITY, ACTIVITY_ID);
    }

    @Test
    void invisibleWorkerLogReturnsNotFoundBeforeHistoryQuery() {
        ActivityLogAccess access = new ActivityLogAccess(
                UUID.randomUUID(), false, Optional.of(EMPLOYEE_ID));
        when(logs.resolveAccess(scope, ACTIVITY_ID)).thenReturn(Optional.of(access));
        when(logs.findById(scope, ACTIVITY_ID, LOG_ID)).thenReturn(Optional.of(log(
                OTHER_PROFILE_ID, OTHER_EMPLOYEE_ID)));

        var service = new ActivityLogReadService(permissions, logs, logReads);

        assertThatThrownBy(() -> service.history(ACTIVITY_ID, LOG_ID, query))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(logReads, never()).findHistory(scope, ACTIVITY_ID, LOG_ID, access, query);
    }

    @Test
    void workerMayReadHistoryForLinkedEmployeeEvenWhenAnotherProfileAuthoredIt() {
        ActivityLogAccess access = new ActivityLogAccess(
                UUID.randomUUID(), false, Optional.of(EMPLOYEE_ID));
        ActivityLogPage page = new ActivityLogPage(List.of(log(OTHER_PROFILE_ID, EMPLOYEE_ID)), 25, 0, false);
        when(logs.resolveAccess(scope, ACTIVITY_ID)).thenReturn(Optional.of(access));
        when(logs.findById(scope, ACTIVITY_ID, LOG_ID)).thenReturn(
                Optional.of(log(OTHER_PROFILE_ID, EMPLOYEE_ID)));
        when(logReads.findHistory(scope, ACTIVITY_ID, LOG_ID, access, query)).thenReturn(page);

        var service = new ActivityLogReadService(permissions, logs, logReads);
        service.history(ACTIVITY_ID, LOG_ID, query);

        verify(logReads).findHistory(scope, ACTIVITY_ID, LOG_ID, access, query);
    }

    @Test
    void invisibleActivityIsNonEnumeratingNotFound() {
        when(logs.resolveAccess(scope, ACTIVITY_ID)).thenReturn(Optional.empty());
        var service = new ActivityLogReadService(permissions, logs, logReads);

        assertThatThrownBy(() -> service.list(ACTIVITY_ID, query))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(logReads, never()).findAll(scope, ACTIVITY_ID, null, query);
    }

    private ActivityLogRecord log(UUID authorProfileId, UUID employeeId) {
        return new ActivityLogRecord(
                LOG_ID,
                TENANT_ID,
                ACTIVITY_ID,
                employeeId,
                authorProfileId,
                Instant.parse("2026-07-23T00:00:00Z"),
                Optional.of("work"),
                Optional.of(BigDecimal.ONE),
                Optional.of(ActivityLogUnit.KG),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0);
    }
}
