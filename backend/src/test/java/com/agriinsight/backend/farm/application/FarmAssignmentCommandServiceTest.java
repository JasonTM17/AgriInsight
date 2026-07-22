package com.agriinsight.backend.farm.application;

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

class FarmAssignmentCommandServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID FARM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("39000000-0000-0000-0000-000000000001");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.of("farm-assignment-command-1"));

    private final FarmAssignmentService assignments = mock(FarmAssignmentService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final FarmAssignmentCommandService service =
            new FarmAssignmentCommandService(assignments, commandExecutions);

    @Test
    void grantTargetsAreVisibleBeforeClaimingIdempotencyKey() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = grant();
        doThrow(new AccessDeniedException("denied"))
                .when(assignments).requireGrantTargets(command);

        assertThatThrownBy(() -> service.grant(request, command))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(commandExecutions);
    }

    @Test
    void revokeTargetIsVisibleBeforeClaimingIdempotencyKey() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(assignments).getForManagement(ASSIGNMENT_ID);

        assertThatThrownBy(() -> service.revoke(
                request, ASSIGNMENT_ID, new FarmAssignmentCommands.Revoke(0, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedGrantEntersExecutorAfterTargetValidation() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = grant();
        CommandExecutionResult.Conflict<FarmAssignmentRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.grant(request, command)).isEqualTo(conflict);

        var order = inOrder(assignments, commandExecutions);
        order.verify(assignments).requireGrantTargets(command);
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private FarmAssignmentCommands.Grant grant() {
        return new FarmAssignmentCommands.Grant(PROFILE_ID, FARM_ID, 0, AUDIT);
    }
}
