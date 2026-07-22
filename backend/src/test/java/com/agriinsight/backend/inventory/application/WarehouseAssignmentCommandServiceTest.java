package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WarehouseAssignmentCommandServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("55000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("53000000-0000-0000-0000-000000000004");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    private final WarehouseAssignmentService assignments = mock(WarehouseAssignmentService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final WarehouseAssignmentCommandService service =
            new WarehouseAssignmentCommandService(assignments, executions);

    @Test
    void targetValidationPrecedesGrantIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = grantCommand();
        doThrow(new AccessDeniedException("denied"))
                .when(assignments).requireGrantTargets(command);

        assertThatThrownBy(() -> service.grant(request, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void authorizedGrantClaimsAfterTargetValidation() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = grantCommand();
        CommandExecutionResult.Conflict<WarehouseAssignmentRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(executions).execute(eq(request), any(), any());

        assertThat(service.grant(request, command)).isEqualTo(conflict);

        var order = inOrder(assignments, executions);
        order.verify(assignments).requireGrantTargets(command);
        order.verify(executions).execute(eq(request), any(), any());
    }

    @Test
    void scopedLookupPrecedesRevokeIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(assignments).getForManagement(ASSIGNMENT_ID);

        assertThatThrownBy(() -> service.revoke(
                request, ASSIGNMENT_ID, new WarehouseAssignmentCommands.Revoke(0, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    private WarehouseAssignmentCommands.Grant grantCommand() {
        return new WarehouseAssignmentCommands.Grant(PROFILE_ID, WAREHOUSE_ID, 0, AUDIT);
    }
}
