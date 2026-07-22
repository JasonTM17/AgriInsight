package com.agriinsight.backend.operations.application;

import static com.agriinsight.backend.operations.application.ActivityApplicationTestFixtures.AUDIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class HarvestCommandServiceTest {

    private final HarvestService harvests = mock(HarvestService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final HarvestCommandService service = new HarvestCommandService(harvests, executions);

    @Test
    void targetAuthorizationPrecedesIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        HarvestCommands.Post command = command();
        doThrow(new AccessDeniedException("denied")).when(harvests).requirePostTarget(command);

        assertThatThrownBy(() -> service.post(request, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }

    private HarvestCommands.Post command() {
        return new HarvestCommands.Post(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2027-09-01"), new BigDecimal("100"), new BigDecimal("2"),
                Optional.of("A"), Optional.of(new BigDecimal("2500000")), AUDIT);
    }
}
