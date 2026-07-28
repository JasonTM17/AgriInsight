package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.integration.infrastructure.RealtimeWorkerProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class RealtimeWorkerRoleVerifierTest {

    @Test
    void acceptsCompleteSourceEvidenceWithAnIndexEligibleBoundedProbe() throws Exception {
        VerificationHarness harness = verifier(false);

        assertThatCode(harness.verifier()::verify).doesNotThrowAnyException();

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(harness.connection()).prepareStatement(query.capture());
        assertThat(query.getValue())
                .contains(
                        "SELECT EXISTS",
                        "FROM realtime_operational_alerts",
                        "WHERE (",
                        "source_occurred_at IS NULL",
                        "policy_code = 'OUTBOX_PUBLISH_BACKLOG'",
                        "source_event_id IS NOT NULL",
                        "policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')",
                        "source_event_id IS NULL",
                        "LIMIT 1")
                .doesNotContain("SET ");
        verify(harness.statement()).setQueryTimeout(20);
    }

    @Test
    void rejectsAnyInvalidSourceEvidence() throws Exception {
        VerificationHarness harness = verifier(true);

        assertThatThrownBy(harness.verifier()::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker source evidence backfill is incomplete");
    }

    @SuppressWarnings("unchecked")
    private static VerificationHarness verifier(boolean invalidSourceEvidence) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("current_user = CAST"),
                        eq(Boolean.class),
                        eq("agriinsight_alert_worker")))
                .thenReturn(true);

        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(invalidSourceEvidence);
        doAnswer(invocation -> {
                    ConnectionCallback<Boolean> callback = invocation.getArgument(0);
                    return callback.doInConnection(connection);
                })
                .when(jdbcTemplate)
                .execute(any(ConnectionCallback.class));

        return new VerificationHarness(
                new RealtimeWorkerRoleVerifier(
                        jdbcTemplate, workerProperties(), alertProperties(), kafkaProperties()),
                connection,
                statement);
    }

    private static RealtimeWorkerProperties workerProperties() {
        return new RealtimeWorkerProperties(
                false,
                false,
                "realtime-worker-1",
                20,
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofSeconds(20),
                "agriinsight.operational.v1",
                "agriinsight.operational.v1.dlt",
                3,
                (short) 1,
                262_144);
    }

    private static RealtimeAlertWorkerProperties alertProperties() {
        return new RealtimeAlertWorkerProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                2,
                100,
                Duration.ofSeconds(20),
                "agriinsight-alert-observer-v1",
                "agriinsight.operational.v1.alert-observer-failure",
                2,
                Duration.ofMillis(500));
    }

    private static KafkaProperties kafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.getConsumer().setGroupId("agriinsight-realtime-v1");
        return properties;
    }

    private record VerificationHarness(
            RealtimeWorkerRoleVerifier verifier, Connection connection, PreparedStatement statement) {
    }
}
