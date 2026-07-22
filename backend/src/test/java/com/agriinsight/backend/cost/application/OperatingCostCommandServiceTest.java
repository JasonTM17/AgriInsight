package com.agriinsight.backend.cost.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostTarget;
import com.agriinsight.backend.shared.application.CommandExecutionRequest;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OperatingCostCommandServiceTest {

    private final OperatingCostService costs = mock(OperatingCostService.class);
    private final CommandExecutionService executions = mock(CommandExecutionService.class);
    private final OperatingCostCommandService service =
            new OperatingCostCommandService(costs, executions);

    @Test
    void targetAuthorizationPrecedesIdempotencyClaim() {
        CommandExecutionRequest request = mock(CommandExecutionRequest.class);
        CostCommands.Post command = new CostCommands.Post(
                CostTarget.tenant(), CostCategory.OTHER, BigDecimal.ONE,
                Instant.parse("2027-09-01T00:00:00Z"), Optional.empty(), Optional.empty(),
                new TenantAuditMetadata(Optional.empty(), Optional.empty()));
        doThrow(new AccessDeniedException("denied")).when(costs).requirePostTarget(command);

        assertThatThrownBy(() -> service.post(request, command))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(executions);
    }
}
