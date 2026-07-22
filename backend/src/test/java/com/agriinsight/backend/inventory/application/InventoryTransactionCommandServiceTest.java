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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class InventoryTransactionCommandServiceTest {

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    private final InventoryTransactionService inventory = mock(InventoryTransactionService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final InventoryTransactionCommandService service =
            new InventoryTransactionCommandService(inventory, executions);

    @Test
    void postingAuthorizationPrecedesIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = issue();
        doThrow(new AccessDeniedException("denied"))
                .when(inventory).requirePostingTarget(command);

        assertThatThrownBy(() -> service.post(request, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void reversalValidationPrecedesIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = new InventoryTransactionCommands.Reversal(
                BigDecimal.ONE, "Correction", 0, AUDIT);
        doThrow(new AccessDeniedException("denied"))
                .when(inventory).requireReversalTarget(TRANSACTION_ID, command);

        assertThatThrownBy(() -> service.reverse(request, TRANSACTION_ID, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void authorizedPostingClaimsAfterTargetValidation() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        var command = issue();
        CommandExecutionResult.Conflict<InventoryTransactionRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(executions).execute(eq(request), any(), any());

        assertThat(service.post(request, command)).isEqualTo(conflict);

        var order = inOrder(inventory, executions);
        order.verify(inventory).requirePostingTarget(command);
        order.verify(executions).execute(eq(request), any(), any());
    }

    private InventoryTransactionCommands.Issue issue() {
        return new InventoryTransactionCommands.Issue(
                WAREHOUSE_ID, MATERIAL_ID, BigDecimal.ONE, Optional.empty(),
                Instant.parse("2027-01-01T08:00:00Z"), "Field use", Optional.empty(), AUDIT);
    }
}
