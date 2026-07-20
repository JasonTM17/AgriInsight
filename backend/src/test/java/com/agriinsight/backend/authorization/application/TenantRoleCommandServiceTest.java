package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class TenantRoleCommandServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID COMMAND_ID = UUID.fromString("23000000-0000-0000-0000-000000000002");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.of("request-2"));

    private final TenantRoleAssignmentService assignments = mock(TenantRoleAssignmentService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final TenantRoleCommandService service = new TenantRoleCommandService(assignments, commandExecutions);

    @Test
    void authorizesBeforeLookingUpOrClaimingAnIdempotencyKey() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(assignments).requireRoleManagement();

        assertThatThrownBy(() -> service.grant(request, PROFILE_ID, grantCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void anAuthorizedRequestEntersTheIdempotentExecutorOnlyAfterAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<TenantRoleAssignment> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.grant(request, PROFILE_ID, grantCommand())).isEqualTo(conflict);

        var order = inOrder(assignments, commandExecutions);
        order.verify(assignments).requireRoleManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private TenantRoleAssignmentCommands.Grant grantCommand() {
        return new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 0, AUDIT);
    }
}
