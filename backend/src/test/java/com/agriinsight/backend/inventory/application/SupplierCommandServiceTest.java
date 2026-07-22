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

class SupplierCommandServiceTest {

    private static final UUID SUPPLIER_ID =
            UUID.fromString("54000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID =
            UUID.fromString("53000000-0000-0000-0000-000000000003");
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("SUPPLIER_CHANGE"), Optional.of("request-supplier-1"));

    private final SupplierService suppliers = mock(SupplierService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final SupplierCommandService service =
            new SupplierCommandService(suppliers, commandExecutions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(suppliers).requireTenantManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void tenantAuthorizationPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(suppliers).requireTenantManagement();

        assertThatThrownBy(() -> service.update(request, SUPPLIER_ID, updateCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedPatchEntersExecutorAfterTenantAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<SupplierRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.update(request, SUPPLIER_ID, updateCommand())).isEqualTo(conflict);

        var order = inOrder(suppliers, commandExecutions);
        order.verify(suppliers).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    @Test
    void lifecycleAuthorizationRemainsTenantWide() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(suppliers).requireTenantManagement();

        assertThatThrownBy(() -> service.deactivate(
                request, SUPPLIER_ID, new SupplierCommands.Lifecycle(2, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedCreateEntersExecutorAfterTenantAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<SupplierRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.create(request, createCommand())).isEqualTo(conflict);

        var order = inOrder(suppliers, commandExecutions);
        order.verify(suppliers).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private SupplierCommands.Create createCommand() {
        return new SupplierCommands.Create("SUP-A", "Supplier A", AUDIT);
    }

    private SupplierCommands.Update updateCommand() {
        return new SupplierCommands.Update(
                Optional.empty(), Optional.of("Updated Supplier"), 2, AUDIT);
    }
}
