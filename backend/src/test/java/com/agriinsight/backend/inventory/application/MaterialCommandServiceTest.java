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
import com.agriinsight.backend.inventory.domain.CanonicalUnit;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MaterialCommandServiceTest {

    private static final UUID MATERIAL_ID =
            UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID =
            UUID.fromString("53000000-0000-0000-0000-000000000002");
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("MATERIAL_CHANGE"), Optional.of("request-material-1"));

    private final MaterialService materials = mock(MaterialService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final MaterialCommandService service =
            new MaterialCommandService(materials, commandExecutions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(materials).requireTenantManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void tenantAuthorizationPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(materials).requireTenantManagement();

        assertThatThrownBy(() -> service.update(request, MATERIAL_ID, updateCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedPatchEntersExecutorAfterTenantAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<MaterialRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.update(request, MATERIAL_ID, updateCommand())).isEqualTo(conflict);

        var order = inOrder(materials, commandExecutions);
        order.verify(materials).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    @Test
    void lifecycleAuthorizationRemainsTenantWide() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(materials).requireTenantManagement();

        assertThatThrownBy(() -> service.deactivate(
                request, MATERIAL_ID, new MaterialCommands.Lifecycle(2, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedCreateEntersExecutorAfterTenantAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<MaterialRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.create(request, createCommand())).isEqualTo(conflict);

        var order = inOrder(materials, commandExecutions);
        order.verify(materials).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private MaterialCommands.Create createCommand() {
        return new MaterialCommands.Create(
                "FERT-A", "Fertilizer", CanonicalUnit.KG, Optional.empty(), AUDIT);
    }

    private MaterialCommands.Update updateCommand() {
        return new MaterialCommands.Update(
                Optional.empty(), Optional.of("Updated Fertilizer"), Optional.empty(),
                Optional.empty(), 2, AUDIT);
    }
}
