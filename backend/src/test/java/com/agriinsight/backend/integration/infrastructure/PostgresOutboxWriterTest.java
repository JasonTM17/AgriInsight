package com.agriinsight.backend.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.application.OperationalEventRecord;
import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import com.agriinsight.backend.shared.application.CommandCommittedEvent;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PostgresOutboxWriterTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PRINCIPAL_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID COMMAND_ID = UUID.fromString("77000000-0000-0000-0000-000000000101");
    private static final UUID AGGREGATE_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-28T06:41:19.123456789Z");
    private static final Instant POSTGRES_OCCURRED_AT = Instant.parse("2026-07-28T06:41:19.123456Z");

    @Test
    void persistsTheSamePostgresPrecisionTimestampInTheRowAndEnvelope() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        JsonMapper jsonMapper = new JsonMapper();

        new PostgresOutboxWriter(jdbcTemplate, jsonMapper).append(command());

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        Object[] values = parameters.getValue();
        Timestamp persistedOccurredAt = (Timestamp) values[9];
        String payload = (String) values[10];
        JsonNode envelope = jsonMapper.readTree(payload);

        assertThat(persistedOccurredAt.toInstant()).isEqualTo(POSTGRES_OCCURRED_AT);
        assertThat(envelope.get("occurred_at").asString()).isEqualTo(POSTGRES_OCCURRED_AT.toString());
        assertThatCode(() -> OperationalEventRecord.from(
                        persistedEvent(UUID.fromString(envelope.get("event_id").asString()), payload),
                        jsonMapper,
                        262_144))
                .doesNotThrowAnyException();
    }

    private static CommandCommittedEvent command() {
        return new CommandCommittedEvent(
                TENANT_ID,
                PRINCIPAL_ID,
                COMMAND_ID,
                "/api/v1/farms",
                new CommandTarget("FARM", AGGREGATE_ID, 0),
                Optional.of("realtime-e2e"),
                OCCURRED_AT,
                0);
    }

    private static OutboxEvent persistedEvent(UUID eventId, String payload) {
        return new OutboxEvent(
                eventId,
                TENANT_ID,
                COMMAND_ID,
                0,
                "FARM",
                AGGREGATE_ID,
                0,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                CommandCommittedEvent.SCHEMA_VERSION,
                POSTGRES_OCCURRED_AT,
                payload,
                OutboxStatus.PENDING,
                0,
                5,
                POSTGRES_OCCURRED_AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty());
    }
}
