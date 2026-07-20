package com.agriinsight.backend.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiCommandRecordTest {

    private static final UUID COMMAND_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PRINCIPAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String KEY_DIGEST = "a".repeat(64);
    private static final String COMMAND_HASH = "b".repeat(64);

    @Test
    void reservationCompletesWithOnlyReplaySafeMetadata() {
        ApiCommandRecord reservation = reservation();

        ApiCommandRecord completed = reservation.complete(200, "USER_PROFILE", RESOURCE_ID, 7);

        assertThat(completed.state()).isEqualTo(ApiCommandRecord.State.COMPLETED);
        assertThat(completed.responseStatus()).contains(200);
        assertThat(completed.targetResourceType()).contains("USER_PROFILE");
        assertThat(completed.targetResourceId()).contains(RESOURCE_ID);
        assertThat(completed.targetVersion()).contains(7L);
        assertThat(completed.matches((short) 1, COMMAND_HASH)).isTrue();
        assertThat(completed.matches((short) 2, COMMAND_HASH)).isFalse();
        assertThat(completed.toString()).doesNotContain("raw-key", "displayName");
    }

    @Test
    void completionShapeCannotBePartialOrInvalid() {
        assertThatThrownBy(() -> new ApiCommandRecord(
                        COMMAND_ID,
                        TENANT_ID,
                        PRINCIPAL_ID,
                        "PATCH",
                        "/api/v1/users/{id}",
                        KEY_DIGEST,
                        (short) 1,
                        COMMAND_HASH,
                        ApiCommandRecord.State.COMPLETED,
                        Optional.of(200),
                        Optional.of("USER_PROFILE"),
                        Optional.empty(),
                        Optional.of(7L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completion metadata");
        assertThatThrownBy(() -> reservation().complete(409, "USER_PROFILE", RESOURCE_ID, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responseStatus");
    }

    private ApiCommandRecord reservation() {
        return ApiCommandRecord.inProgress(
                COMMAND_ID,
                TENANT_ID,
                PRINCIPAL_ID,
                "PATCH",
                "/api/v1/users/{id}",
                KEY_DIGEST,
                (short) 1,
                COMMAND_HASH);
    }
}
