package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ActivityAssignmentCommandServiceTest {

    private static final UUID EMPLOYEE_ID = UUID.fromString("37000000-0000-0000-0000-000000000001");

    private final ActivityAssignmentService assignments = mock(ActivityAssignmentService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final ActivityAssignmentCommandService service =
            new ActivityAssignmentCommandService(assignments, executions);

    @Test
    void activityAndEmployeeAuthorizationPrecedeIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        ActivityAssignmentCommands.Grant command =
                new ActivityAssignmentCommands.Grant(EMPLOYEE_ID, 0, AUDIT);
        doThrow(new AccessDeniedException("denied"))
                .when(assignments).requireGrantTarget(ACTIVITY_ID, command);

        assertThatThrownBy(() -> service.grant(request, ACTIVITY_ID, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }
}
