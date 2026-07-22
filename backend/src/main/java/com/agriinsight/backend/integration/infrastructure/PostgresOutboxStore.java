package com.agriinsight.backend.integration.infrastructure;

import com.agriinsight.backend.integration.application.OutboxDrainService;
import com.agriinsight.backend.integration.application.OutboxStore;
import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class PostgresOutboxStore implements OutboxStore {

    private static final RowMapper<OutboxEvent> MAPPER = (result, rowNumber) -> new OutboxEvent(
            result.getObject("id", UUID.class),
            result.getObject("tenant_id", UUID.class),
            result.getObject("command_id", UUID.class),
            result.getInt("event_ordinal"),
            result.getString("aggregate_type"),
            result.getObject("aggregate_id", UUID.class),
            result.getLong("aggregate_version"),
            result.getString("event_type"),
            result.getInt("schema_version"),
            result.getTimestamp("occurred_at").toInstant(),
            result.getString("payload"),
            OutboxStatus.valueOf(result.getString("status")),
            result.getInt("attempts"),
            result.getInt("max_attempts"),
            result.getTimestamp("available_at").toInstant(),
            optionalInstant(result.getTimestamp("leased_until")),
            optionalInstant(result.getTimestamp("published_at")),
            optionalInstant(result.getTimestamp("dead_lettered_at")),
            Optional.ofNullable(result.getString("lease_owner")),
            Optional.ofNullable(result.getObject("lease_token", UUID.class)),
            result.getLong("lease_generation"),
            Optional.ofNullable(result.getString("last_error")));

    private final JdbcTemplate jdbcTemplate;

    public PostgresOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public List<OutboxDrainService.OutboxLease> lease(
            String owner, int limit, Duration leaseDuration, Instant now) {
        Timestamp current = Timestamp.from(now);
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                   SET status = 'DEAD_LETTER', dead_lettered_at = ?, leased_until = NULL,
                       lease_owner = NULL, lease_token = NULL,
                       last_error = COALESCE(last_error, 'lease expired at max attempts')
                 WHERE status = 'LEASED' AND leased_until < ? AND attempts >= max_attempts
                """,
                current, current);

        List<OutboxDrainService.OutboxLease> result = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            UUID token = UUID.randomUUID();
            List<OutboxEvent> claimed = jdbcTemplate.query(
                    """
                    WITH candidate AS (
                        SELECT event.id
                          FROM outbox_events AS event
                         WHERE ((event.status = 'PENDING' AND event.available_at <= ?)
                            OR (event.status = 'LEASED' AND event.leased_until < ?))
                           AND event.attempts < event.max_attempts
                           AND NOT EXISTS (
                               SELECT 1
                                 FROM outbox_events AS predecessor
                                WHERE predecessor.tenant_id = event.tenant_id
                                  AND predecessor.aggregate_type = event.aggregate_type
                                  AND predecessor.aggregate_id = event.aggregate_id
                                  AND predecessor.aggregate_version < event.aggregate_version
                                  AND predecessor.status <> 'PUBLISHED')
                         ORDER BY event.available_at, event.occurred_at, event.id
                         LIMIT 1
                         FOR UPDATE SKIP LOCKED
                    )
                    UPDATE outbox_events AS event
                       SET status = 'LEASED', attempts = event.attempts + 1,
                           leased_until = ?, lease_owner = ?, lease_token = ?,
                           lease_generation = event.lease_generation + 1
                      FROM candidate
                     WHERE event.id = candidate.id
                    RETURNING event.*
                    """,
                    MAPPER,
                    current,
                    current,
                    Timestamp.from(now.plus(leaseDuration)),
                    owner,
                    token);
            if (claimed.isEmpty()) {
                break;
            }
            OutboxEvent event = claimed.get(0);
            result.add(new OutboxDrainService.OutboxLease(
                    event, owner, token, event.leaseGeneration()));
        }
        return result;
    }

    @Override
    public boolean acknowledge(OutboxDrainService.OutboxLease lease, Instant now) {
        return jdbcTemplate.update(
                """
                UPDATE outbox_events
                   SET status = 'PUBLISHED', published_at = ?, leased_until = NULL,
                       lease_owner = NULL, lease_token = NULL
                 WHERE id = ? AND status = 'LEASED' AND lease_owner = ?
                   AND lease_token = ? AND lease_generation = ? AND leased_until > ?
                """,
                Timestamp.from(now), lease.event().id(), lease.owner(), lease.token(),
                lease.generation(), Timestamp.from(now)) == 1;
    }

    @Override
    public OutboxDrainService.FailureResult fail(
            OutboxDrainService.OutboxLease lease, String error, Instant now, Duration backoff) {
        int requeued = jdbcTemplate.update(
                """
                UPDATE outbox_events
                   SET status = CASE WHEN attempts >= max_attempts
                                     THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                       available_at = ?, leased_until = NULL, lease_owner = NULL,
                       lease_token = NULL,
                       dead_lettered_at = CASE WHEN attempts >= max_attempts
                                               THEN CAST(? AS timestamptz) ELSE NULL END,
                       last_error = ?
                 WHERE id = ? AND status = 'LEASED' AND lease_owner = ?
                   AND lease_token = ? AND lease_generation = ? AND leased_until > ?
                """,
                Timestamp.from(now.plus(backoff)),
                Timestamp.from(now),
                error,
                lease.event().id(),
                lease.owner(),
                lease.token(),
                lease.generation(),
                Timestamp.from(now));
        if (requeued == 0) {
            return OutboxDrainService.FailureResult.STALE;
        }
        return lease.event().attempts() >= lease.event().maxAttempts()
                ? OutboxDrainService.FailureResult.DEAD_LETTER
                : OutboxDrainService.FailureResult.REQUEUED;
    }

    private static Optional<Instant> optionalInstant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }
}
