package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OutboxWriter;
import com.agriinsight.backend.shared.application.CommandCommittedEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresOutboxWriter implements OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public PostgresOutboxWriter(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper is required");
    }

    @Override
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void append(CommandCommittedEvent event) {
        CommandCommittedEvent required = Objects.requireNonNull(event, "event is required");
        Instant occurredAt = required.occurredAt().truncatedTo(ChronoUnit.MICROS);
        try {
            UUID eventId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO outbox_events (
                        id, tenant_id, command_id, event_ordinal, aggregate_type,
                        aggregate_id, aggregate_version, event_type, schema_version,
                        occurred_at, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """,
                    eventId,
                    required.tenantId(),
                    required.commandId(),
                    required.eventOrdinal(),
                    required.target().resourceType(),
                    required.target().resourceId(),
                    required.target().resourceVersion(),
                    required.eventType(),
                    CommandCommittedEvent.SCHEMA_VERSION,
                    Timestamp.from(occurredAt),
                    jsonMapper.writeValueAsString(envelope(eventId, required, occurredAt)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize outbox payload", exception);
        }
    }

    private static Map<String, Object> envelope(
            UUID eventId, CommandCommittedEvent event, Instant occurredAt) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", eventId);
        envelope.put("tenant_id", event.tenantId());
        envelope.put("command_id", event.commandId());
        envelope.put("event_ordinal", event.eventOrdinal());
        envelope.put("aggregate", event.target().resourceType());
        envelope.put("aggregate_id", event.target().resourceId());
        envelope.put("aggregate_version", event.target().resourceVersion());
        envelope.put("business_code", null);
        envelope.put("event_type", event.eventType());
        envelope.put("schema_version", CommandCommittedEvent.SCHEMA_VERSION);
        envelope.put("occurred_at", occurredAt.toString());
        envelope.put("payload", event.payload());
        return envelope;
    }
}
