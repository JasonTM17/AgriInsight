package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OutboxWriter;
import com.agriinsight.backend.shared.application.CommandCommittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresOutboxWriter implements OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    }

    @Override
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void append(CommandCommittedEvent event) {
        CommandCommittedEvent required = Objects.requireNonNull(event, "event is required");
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
                    Timestamp.from(required.occurredAt()),
                    objectMapper.writeValueAsString(envelope(eventId, required)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox payload", exception);
        }
    }

    private static Map<String, Object> envelope(UUID eventId, CommandCommittedEvent event) {
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
        envelope.put("occurred_at", event.occurredAt().toString());
        envelope.put("payload", event.payload());
        return envelope;
    }
}
