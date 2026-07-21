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
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FarmCommandServiceTest {

    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.of("MASTER_DATA_CHANGE"), Optional.of("request-1"));

    private final FarmService farms = mock(FarmService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final FarmCommandService service = new FarmCommandService(farms, commandExecutions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(farms).requireTenantManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void assignedFarmVisibilityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Farm")).when(farms).getForFarmManagement(FARM_ID);

        assertThatThrownBy(() -> service.update(request, FARM_ID, updateCommand()))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedPatchEntersExecutorOnlyAfterScopedFarmLookup() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<FarmRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.update(request, FARM_ID, updateCommand())).isEqualTo(conflict);

        var order = inOrder(farms, commandExecutions);
        order.verify(farms).getForFarmManagement(FARM_ID);
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    @Test
    void lifecycleAuthorizationRemainsTenantWide() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(farms).requireTenantManagement();

        assertThatThrownBy(() -> service.deactivate(
                request, FARM_ID, new FarmCommands.Lifecycle(2, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedCreateEntersExecutorOnlyAfterAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<FarmRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.create(request, createCommand())).isEqualTo(conflict);

        var order = inOrder(farms, commandExecutions);
        order.verify(farms).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private FarmCommands.Create createCommand() {
        return new FarmCommands.Create("NORTH", "North Farm", AUDIT);
    }

    private FarmCommands.Update updateCommand() {
        return new FarmCommands.Update(Optional.empty(), Optional.of("Updated Farm"), 2, AUDIT);
    }
}
