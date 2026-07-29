package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertEvidence;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertPolicy;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertSeverity;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

final class RealtimeOperationalAlertRowMapper
        implements RowMapper<RealtimeOperationalAlertView> {

    private final Instant generatedAt;

    RealtimeOperationalAlertRowMapper(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public RealtimeOperationalAlertView mapRow(ResultSet result, int rowNumber)
            throws SQLException {
        RealtimeOperationalAlertPolicy policy =
                RealtimeOperationalAlertPolicy.valueOf(result.getString("policy_code"));
        UUID sourceEventId = result.getObject("source_event_id", UUID.class);
        Instant lastObservedAt = result.getTimestamp("last_observed_at").toInstant();
        Timestamp acknowledgedAt = result.getTimestamp("acknowledged_at");
        Optional<Instant> currentAcknowledgement = Optional.ofNullable(acknowledgedAt)
                .map(Timestamp::toInstant);
        return new RealtimeOperationalAlertView(
                result.getObject("id", UUID.class),
                policy,
                RealtimeOperationalAlertSeverity.valueOf(result.getString("severity")),
                result.getString("state"),
                RealtimeOperationalAlertEvidence.from(policy, sourceEventId),
                result.getTimestamp("opened_at").toInstant(),
                result.getTimestamp("source_occurred_at").toInstant(),
                lastObservedAt,
                result.getTimestamp("last_evaluated_at").toInstant(),
                Math.max(0, Duration.between(lastObservedAt, generatedAt).getSeconds()),
                currentAcknowledgement.isPresent(),
                currentAcknowledgement,
                result.getLong("version"));
    }
}
