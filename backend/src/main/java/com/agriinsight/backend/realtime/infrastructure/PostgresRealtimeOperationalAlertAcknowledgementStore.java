package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeAlertAcknowledgement;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Locks the current alert observation before writing one immutable profile revision. */
@Repository
@Profile("!test")
public class PostgresRealtimeOperationalAlertAcknowledgementStore
        implements RealtimeOperationalAlertAcknowledgementStore {

    private static final String ACKNOWLEDGE = """
            SELECT acknowledged_observation_at, created
              FROM agriinsight_security.acknowledge_realtime_operational_alert(?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeOperationalAlertAcknowledgementStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public RealtimeAlertAcknowledgement acknowledge(
            UUID tenantId, UUID profileId, UUID alertId, Instant acknowledgedAt) {
        requireTransaction();
        UUID requiredTenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        UUID requiredProfileId = Objects.requireNonNull(profileId, "profileId is required");
        UUID requiredAlertId = Objects.requireNonNull(alertId, "alertId is required");
        Instant requiredAcknowledgedAt = Objects.requireNonNull(acknowledgedAt, "acknowledgedAt is required");
        List<RealtimeAlertAcknowledgement> acknowledgements = jdbcTemplate.query(
                ACKNOWLEDGE,
                (result, rowNumber) -> new RealtimeAlertAcknowledgement(
                        requiredAlertId,
                        result.getTimestamp("acknowledged_observation_at").toInstant(),
                        result.getBoolean("created")),
                requiredTenantId,
                requiredProfileId,
                requiredAlertId,
                UUID.randomUUID(),
                Timestamp.from(requiredAcknowledgedAt));
        if (acknowledgements.size() != 1) {
            throw new RealtimeOperationalAlertNotFoundException();
        }
        return acknowledgements.getFirst();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("operational alert acknowledgement requires an active transaction");
        }
    }
}
