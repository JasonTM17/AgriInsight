package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeOperationalEventTest {

    @Test
    void rejectsAnEventTypeThatDoesNotMatchItsAggregateType() {
        assertThatThrownBy(() -> new RealtimeOperationalEvent(
                        UUID.fromString("70000000-0000-0000-0000-000000000001"),
                        UUID.fromString("10000000-0000-0000-0000-000000000041"),
                        "FARM",
                        UUID.fromString("71000000-0000-0000-0000-000000000001"),
                        0,
                        "AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED",
                        Instant.parse("2027-09-01T00:00:00Z"),
                        "a".repeat(64),
                        "agriinsight.operational.v1",
                        0,
                        1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventType must match aggregateType");
    }
}
