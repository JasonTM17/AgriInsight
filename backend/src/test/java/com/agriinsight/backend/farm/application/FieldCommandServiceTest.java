package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.FIELD_ID;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.farm.application.FieldApplicationTestFixtures.updateCommand;
import static org.assertj.core.api.Assertions.assertThat;
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

class FieldCommandServiceTest {

    private static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000005");

    private final FieldService fields = mock(FieldService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final FieldCommandService service = new FieldCommandService(fields, executions);

    @Test
    void farmAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(fields).requireFarmManagement(FARM_ID);

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void hiddenParentFarmPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Farm"))
                .when(fields).requireFarmManagement(FARM_ID);

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void scopedFieldVisibilityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Field")).when(fields).getForManagement(FIELD_ID);

        assertThatThrownBy(() -> service.update(request, FIELD_ID, updateCommand(2)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void authorizedPatchEntersExecutorOnlyAfterScopedLookup() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<FieldRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(executions).execute(eq(request), any(), any());

        assertThat(service.update(request, FIELD_ID, updateCommand(2))).isEqualTo(conflict);

        var order = inOrder(fields, executions);
        order.verify(fields).getForManagement(FIELD_ID);
        order.verify(executions).execute(eq(request), any(), any());
    }
}
