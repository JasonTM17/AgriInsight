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
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WarehouseCommandServiceTest {

    private static final UUID WAREHOUSE_ID =
            UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID =
            UUID.fromString("53000000-0000-0000-0000-000000000001");
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("WAREHOUSE_CHANGE"), Optional.of("request-warehouse-1"));

    private final WarehouseService warehouses = mock(WarehouseService.class);
    private final CommandExecutionService commandExecutions = mock(CommandExecutionService.class);
    private final WarehouseCommandService service =
            new WarehouseCommandService(warehouses, commandExecutions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(warehouses).requireTenantManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void assignedWarehouseVisibilityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Warehouse"))
                .when(warehouses).getForWarehouseManagement(WAREHOUSE_ID);

        assertThatThrownBy(() -> service.update(request, WAREHOUSE_ID, updateCommand()))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedPatchEntersExecutorAfterScopedWarehouseLookup() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<WarehouseRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.update(request, WAREHOUSE_ID, updateCommand())).isEqualTo(conflict);

        var order = inOrder(warehouses, commandExecutions);
        order.verify(warehouses).getForWarehouseManagement(WAREHOUSE_ID);
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    @Test
    void lifecycleAuthorizationRemainsTenantWide() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied"))
                .when(warehouses).requireTenantManagement();

        assertThatThrownBy(() -> service.deactivate(
                request, WAREHOUSE_ID, new WarehouseCommands.Lifecycle(2, AUDIT)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(commandExecutions);
    }

    @Test
    void authorizedCreateEntersExecutorAfterTenantAuthorization() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<WarehouseRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(commandExecutions).execute(eq(request), any(), any());

        assertThat(service.create(request, createCommand())).isEqualTo(conflict);

        var order = inOrder(warehouses, commandExecutions);
        order.verify(warehouses).requireTenantManagement();
        order.verify(commandExecutions).execute(eq(request), any(), any());
    }

    private WarehouseCommands.Create createCommand() {
        return new WarehouseCommands.Create(
                "WH-NORTH", "North Warehouse", Optional.empty(), AUDIT);
    }

    private WarehouseCommands.Update updateCommand() {
        return new WarehouseCommands.Update(
                Optional.empty(), Optional.of("Updated Warehouse"), Optional.empty(), 2, AUDIT);
    }
}
