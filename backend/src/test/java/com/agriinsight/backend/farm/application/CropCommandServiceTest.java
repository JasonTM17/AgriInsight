package com.agriinsight.backend.farm.application;

import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.CROP_ID;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.farm.application.CropApplicationTestFixtures.updateCommand;
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

class CropCommandServiceTest {

    private static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000007");

    private final CropService crops = mock(CropService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final CropCommandService service = new CropCommandService(crops, executions);

    @Test
    void tenantAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(crops).requireTenantManagement();

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void tenantScopedVisibilityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Crop"))
                .when(crops).getForTenantManagement(CROP_ID);

        assertThatThrownBy(() -> service.update(request, CROP_ID, updateCommand(2)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void authorizedPatchEntersExecutorOnlyAfterTenantLookup() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CommandExecutionResult.Conflict<CropRecord> conflict =
                new CommandExecutionResult.Conflict<>(COMMAND_ID);
        doReturn(conflict).when(executions).execute(eq(request), any(), any());

        assertThat(service.update(request, CROP_ID, updateCommand(2))).isEqualTo(conflict);

        var order = inOrder(crops, executions);
        order.verify(crops).getForTenantManagement(CROP_ID);
        order.verify(executions).execute(eq(request), any(), any());
    }
}
