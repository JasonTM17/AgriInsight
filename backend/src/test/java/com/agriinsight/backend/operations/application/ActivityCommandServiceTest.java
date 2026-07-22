package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.FARM_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.createCommand;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.updateCommand;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ActivityCommandServiceTest {

    private final ActivityService activities = mock(ActivityService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final ActivityCommandService service = new ActivityCommandService(activities, executions);

    @Test
    void farmAuthorizationPrecedesCreateIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new AccessDeniedException("denied")).when(activities).requireFarmManagement(FARM_ID);

        assertThatThrownBy(() -> service.create(request, createCommand()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void hiddenActivityPrecedesPatchIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        doThrow(new ResourceNotFoundException("Activity"))
                .when(activities).getForManagement(ACTIVITY_ID);

        assertThatThrownBy(() -> service.update(request, ACTIVITY_ID, updateCommand(2)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(executions);
    }

    @Test
    void authorizedPatchLooksUpBeforeEnteringExecutor() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);

        service.update(request, ACTIVITY_ID, updateCommand(2));

        var order = inOrder(activities, executions);
        order.verify(activities).getForManagement(ACTIVITY_ID);
        order.verify(executions).execute(eq(request), any(), any());
    }
}
