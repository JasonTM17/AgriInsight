package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.realtime.infrastructure.RealtimeWorkerRoleVerificationSql.EXPECTED_SCHEMA_VERSION_QUERY;
import static com.agriinsight.backend.realtime.infrastructure.RealtimeWorkerRoleVerificationSql.REQUIRED_GRANTS_MIGRATION;
import static com.agriinsight.backend.realtime.infrastructure.RealtimeWorkerRoleVerificationSql.REQUIRED_GRANTS_MIGRATION_QUERY;
import static com.agriinsight.backend.realtime.infrastructure.RealtimeWorkerRoleVerificationSql.REQUIRED_SCHEMA_VERSION;
import static com.agriinsight.backend.realtime.infrastructure.RealtimeWorkerRoleVerificationSql.ROLE_TOPOLOGY_QUERY;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fails startup unless the isolated alert worker has the released schema and narrow login topology. */
public final class RealtimeWorkerRoleVerifier {

    private static final String REQUIRED_LOGIN = "agriinsight_alert_worker";
    private static final String SOURCE_EVIDENCE_READINESS_QUERY = """
            SELECT EXISTS (
                SELECT 1
                  FROM realtime_operational_alerts
                 WHERE (
                     source_occurred_at IS NULL
                     OR (policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                         AND source_event_id IS NOT NULL)
                     OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
                         AND source_event_id IS NULL)
                 )
                 LIMIT 1
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RealtimeWorkerProperties workerProperties;
    private final RealtimeAlertWorkerProperties alertProperties;
    private final KafkaProperties kafkaProperties;

    public RealtimeWorkerRoleVerifier(
            JdbcTemplate jdbcTemplate,
            RealtimeWorkerProperties workerProperties,
            RealtimeAlertWorkerProperties alertProperties,
            KafkaProperties kafkaProperties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.workerProperties = Objects.requireNonNull(workerProperties, "workerProperties is required");
        this.alertProperties = Objects.requireNonNull(alertProperties, "alertProperties is required");
        this.kafkaProperties = Objects.requireNonNull(kafkaProperties, "kafkaProperties is required");
    }

    public void verify() {
        verifyProcessIsolation();
        verifyReleasedDatabaseContract();
        verifyDatabaseRole();
        if (hasInvalidSourceEvidence()) {
            throw new IllegalStateException(
                    "operational alert worker source evidence backfill is incomplete");
        }
    }

    private void verifyProcessIsolation() {
        if (workerProperties.publisherEnabled() || workerProperties.consumerEnabled()) {
            throw new IllegalStateException("operational alert worker cannot enable the legacy realtime pipeline");
        }
        if (workerProperties.topic().equals(alertProperties.observerFailureTopic())
                || workerProperties.deadLetterTopic().equals(alertProperties.observerFailureTopic())) {
            throw new IllegalStateException(
                    "observer failure topic must differ from the primary and observed DLT topics");
        }
        String legacyConsumerGroup = kafkaProperties.getConsumer().getGroupId();
        if (legacyConsumerGroup == null || legacyConsumerGroup.isBlank()) {
            throw new IllegalStateException("legacy consumer group must be explicit for worker isolation");
        }
        if (legacyConsumerGroup.equals(alertProperties.observerGroupId())) {
            throw new IllegalStateException("DLT observer group must differ from the legacy consumer group");
        }
    }

    private void verifyReleasedDatabaseContract() {
        try {
            Boolean releasedSchemaInstalled = jdbcTemplate.queryForObject(
                    EXPECTED_SCHEMA_VERSION_QUERY, Boolean.class, REQUIRED_SCHEMA_VERSION);
            if (!Boolean.TRUE.equals(releasedSchemaInstalled)) {
                throw new IllegalStateException(
                        "operational alert worker expected schema version is not installed");
            }
            Boolean requiredGrantsCurrent = jdbcTemplate.queryForObject(
                    REQUIRED_GRANTS_MIGRATION_QUERY, Boolean.class, REQUIRED_GRANTS_MIGRATION);
            if (!Boolean.TRUE.equals(requiredGrantsCurrent)) {
                throw new IllegalStateException(
                        "operational alert worker required grants migration is not current");
            }
        } catch (DataAccessException exception) {
            throw new IllegalStateException("operational alert worker schema verification failed");
        }
    }

    private void verifyDatabaseRole() {
        Boolean verified = jdbcTemplate.queryForObject(
                ROLE_TOPOLOGY_QUERY, Boolean.class, REQUIRED_LOGIN);
        if (!Boolean.TRUE.equals(verified)) {
            throw new IllegalStateException("operational alert worker database role verification failed");
        }
    }

    private boolean hasInvalidSourceEvidence() {
        Boolean invalidSourceEvidence = jdbcTemplate.execute(
                (ConnectionCallback<Boolean>) connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(SOURCE_EVIDENCE_READINESS_QUERY)) {
                        statement.setQueryTimeout(
                                Math.toIntExact(alertProperties.maximumQueryDuration().toSeconds()));
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                return true;
                            }
                            return resultSet.getBoolean(1);
                        }
                    }
                });
        return !Boolean.FALSE.equals(invalidSourceEvidence);
    }
}
