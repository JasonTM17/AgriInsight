package com.agriinsight.backend.realtime.infrastructure;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertQueryStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class PostgresRealtimeOperationalAlertQueryStore
        implements RealtimeOperationalAlertQueryStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRealtimeOperationalAlertQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
    }

    @Override
    public List<RealtimeOperationalAlertView> findLatestOpen(
            UUID tenantId,
            UUID profileId,
            Instant generatedAt) {
        return List.copyOf(jdbcTemplate.query(
                RealtimeOperationalAlertQuerySql.LATEST_OPEN,
                new RealtimeOperationalAlertRowMapper(
                        Objects.requireNonNull(generatedAt, "generatedAt is required")),
                Objects.requireNonNull(profileId, "profileId is required"),
                Objects.requireNonNull(tenantId, "tenantId is required")));
    }

    @Override
    public Optional<RealtimeOperationalAlertView> findOpenById(
            UUID tenantId,
            UUID profileId,
            UUID alertId,
            Instant generatedAt) {
        List<RealtimeOperationalAlertView> rows = jdbcTemplate.query(
                RealtimeOperationalAlertQuerySql.OPEN_BY_ID,
                new RealtimeOperationalAlertRowMapper(
                        Objects.requireNonNull(generatedAt, "generatedAt is required")),
                Objects.requireNonNull(profileId, "profileId is required"),
                Objects.requireNonNull(tenantId, "tenantId is required"),
                Objects.requireNonNull(alertId, "alertId is required"));
        if (rows.size() > 1) {
            throw new IllegalStateException("Operational alert query returned duplicate rows");
        }
        return rows.stream().findFirst();
    }
}
