package com.agriinsight.backend.identity.application;

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

class TenantUserCommandServiceTest {

    private static final UUID COMMAND_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.of("ACCESS_APPROVED"), Optional.of("request-1"));

    private final TenantUserService tenantUsers = mock(TenantUserService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final TenantUserCommandService service = new TenantUserCommandService(tenantUsers, commandExecutions);

    @Test
    void authorizesBeforeLookingUpOrClaimingAnIdempotencyKey() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        TenantUserCommands.Create command = createCommand();
        doThrow(new AccessDeniedException("denied")).when(tenantUsers).requireUserManagement();

        assertThatThrownBy(() -> service.create(request, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void anAuthorizedRequestEntersTheIdempotentExecutorOnlyAfterAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<TenantUserProfile> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.create(request, createCommand())).isEqualTo(conflict);

        var order = inOrder(tenantUsers, commandExecutions);
        order.verify(tenantUsers).requireUserManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private TenantUserCommands.Create createCommand() {
        return new TenantUserCommands.Create(
                "Mai Tran",
                Optional.of("mai@example.test"),
                "https://identity.example.test/issuer",
                "provider-user-2",
                AUDIT);
    }
}
