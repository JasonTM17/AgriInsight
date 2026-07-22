package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.EMPLOYEE_ID;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.operations.application.EmployeeApplicationTestFixtures.updateCommand;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class EmployeeCommandServiceTest {

    private static final UUID COMMAND_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000007");

    private final EmployeeService employees = mock(EmployeeService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final EmployeeCommandService service = new EmployeeCommandService(employees, executions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(employees).requireManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(executions);
    }

    @Test
    void tenantScopedVisibilityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Employee"))
                .when(employees).getForManagement(EMPLOYEE_ID);

        assertThatThrownBy(() -> service.update(request, EMPLOYEE_ID, updateCommand(2)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(executions);
    }

    @Test
    void authorizedPatchEntersExecutorOnlyAfterTenantLookup() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<EmployeeRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(executions).execute(eq(request), any(), any());

        service.update(request, EMPLOYEE_ID, updateCommand(2));

        var order = inOrder(employees, executions);
        order.verify(employees).getForManagement(EMPLOYEE_ID);
        order.verify(executions).execute(eq(request), any(), any());
    }
}
