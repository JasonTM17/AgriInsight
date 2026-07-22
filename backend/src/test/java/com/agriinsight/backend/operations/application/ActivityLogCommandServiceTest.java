package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.ACTIVITY_ID;
import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ActivityLogCommandServiceTest {

    private static final UUID EMPLOYEE_ID =
            UUID.fromString("37000000-0000-0000-0000-000000000001");

    private final ActivityLogService logs = mock(ActivityLogService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final ActivityLogCommandService service =
            new ActivityLogCommandService(logs, executions);

    @Test
    void appendAuthorizationPrecedesIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        ActivityLogCommands.Append command = new ActivityLogCommands.Append(
                EMPLOYEE_ID,
                Instant.parse("2027-01-01T01:00:00Z"),
                Optional.of("Harvested"),
                Optional.of(new BigDecimal("100")),
                Optional.of(ActivityLogUnit.KG),
                Optional.empty(),
                AUDIT);
        doThrow(new AccessDeniedException("denied"))
                .when(logs).requireAppendTarget(ACTIVITY_ID, EMPLOYEE_ID);

        assertThatThrownBy(() -> service.append(request, ACTIVITY_ID, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }
}
