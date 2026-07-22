package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.TENANT_ID;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.TENANT_SCOPE;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.employee;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.updateCommand;
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
import com.agriinsight.backend.operations.domain.Employee;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeeMutationServiceTest {

    private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
    private final EmployeeStore store = mock(EmployeeStore.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private EmployeeService service;

    @BeforeEach
    void createService() {
        when(permissions.requireTenant(Permission.WORKFORCE_MANAGE)).thenReturn(TENANT_SCOPE);
        service = new EmployeeService(permissions, store, auditPublisher);
    }

    @Test
    void createsOnlyWithTenantWideManagementAndPublishesAudit() {
        when(store.create(any(), any(Employee.class))).thenAnswer(invocation -> {
            Employee created = invocation.getArgument(1);
            return new EmployeeRecord(
                    created.id(), TENANT_ID, created.code(), created.displayName(),
                    created.jobTitle(), true, 0);
        });

        EmployeeRecord created = service.create(createCommand());

        assertThat(created.active()).isTrue();
        verify(permissions).requireTenant(Permission.WORKFORCE_MANAGE);
        verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }

    @Test
    void stalePatchReturnsTypedConflictWithoutSuccessAudit() {
        when(store.findById(TENANT_SCOPE, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(2, true)))
                .thenReturn(Optional.of(employee(3, true)));
        when(store.update(TENANT_SCOPE, EMPLOYEE_ID, 2, updateCommand(2)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EMPLOYEE_ID, updateCommand(2)))
                .isInstanceOfSatisfying(VersionConflictException.class, conflict -> {
                    assertThat(conflict.expectedVersion()).isEqualTo(2);
                    assertThat(conflict.currentVersion()).isEqualTo(3);
                });
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void deactivationReportsLiveResponsibilityBlockers() {
        when(store.findById(TENANT_SCOPE, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee(4, true)));
        when(store.updateActive(TENANT_SCOPE, EMPLOYEE_ID, 4, false)).thenReturn(Optional.empty());
        when(store.hasDeactivationBlockers(TENANT_SCOPE, EMPLOYEE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(
                EMPLOYEE_ID, new EmployeeCommands.Lifecycle(4,
                        EmployeeApplicationTestFixtures.AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("live");
    }
}
