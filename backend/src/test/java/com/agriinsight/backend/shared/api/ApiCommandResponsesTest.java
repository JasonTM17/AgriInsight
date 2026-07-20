package com.agriinsight.backend.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class ApiCommandResponsesTest {

    private static final UUID COMMAND_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID = UUID.fromString("22000000-0000-0000-0000-000000000001");

    @Test
    void replayResponseEtagCanFollowTheFreshAuthorizedRepresentation() {
        var completed = new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                true,
                200,
                new CommandTarget("USER_ROLE", RESOURCE_ID, 0),
                Optional.of("fresh-view"));

        var response = ApiCommandResponses.body(completed, "fresh-view", 3);

        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(response.getBody()).isEqualTo("fresh-view");
        assertThat(completed.target().resourceVersion()).isZero();
    }

    @Test
    void commandHashConflictBecomesATypedApiConflict() {
        var conflict = new CommandExecutionResult.Conflict<String>(COMMAND_ID);

        assertThatThrownBy(() -> ApiCommandResponses.requireCompleted(conflict))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void commandReceiptsUseTheCommittedTargetVersionByDefault() {
        var completed = new CommandExecutionResult.Completed<>(
                COMMAND_ID,
                false,
                200,
                new CommandTarget("EXTERNAL_IDENTITY", RESOURCE_ID, 4),
                Optional.of("receipt"));

        assertThat(ApiCommandResponses.body(completed, "receipt").getHeaders().getFirst(HttpHeaders.ETAG))
                .isEqualTo("\"4\"");
    }
}
