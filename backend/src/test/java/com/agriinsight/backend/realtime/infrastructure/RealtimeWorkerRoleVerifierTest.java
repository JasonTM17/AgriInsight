package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class RealtimeWorkerRoleVerifierTest {

    private static final String EXPECTED_SCHEMA_VERSION = "27";

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
        verify(harness.jdbcTemplate())
                .queryForObject(
                        contains("FROM public.flyway_schema_history"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION));
        verify(harness.jdbcTemplate())
                .queryForObject(
                        contains("ORDER BY installed_rank DESC"),
                        eq(Boolean.class),
                        eq("R__tenant_rls_helpers_and_grants.sql"));
    }

    @Test
    void rejectsAnyInvalidSourceEvidence() throws Exception {
        VerificationHarness harness = verifier(true);

        assertThatThrownBy(harness.verifier()::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker source evidence backfill is incomplete");
    }

    @Test
    void rejectsMissingExpectedSchemaVersionBeforeRoleAndSourceEvidenceChecks() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("FROM public.flyway_schema_history"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION)))
                .thenReturn(false);
        RealtimeWorkerRoleVerifier verifier = verifier(jdbcTemplate);

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker expected schema version is not installed");

        verify(jdbcTemplate)
                .queryForObject(
                        contains("FROM public.flyway_schema_history"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION));
        verify(jdbcTemplate, never())
                .queryForObject(
                        contains("ORDER BY installed_rank DESC"),
                        eq(Boolean.class),
                        eq("R__tenant_rls_helpers_and_grants.sql"));
        verify(jdbcTemplate, never())
                .queryForObject(
                        contains("current_user = CAST"),
                        eq(Boolean.class),
                        eq("agriinsight_alert_worker"));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void rejectsMissingOrFailedLatestRequiredGrantsMigrationBeforeRoleChecks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("WHERE version = ?"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION)))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                        contains("ORDER BY installed_rank DESC"),
                        eq(Boolean.class),
                        eq("R__tenant_rls_helpers_and_grants.sql")))
                .thenReturn(false);

        assertThatThrownBy(() -> verifier(jdbcTemplate).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker required grants migration is not current");

        verify(jdbcTemplate, never())
                .queryForObject(
                        contains("current_user = CAST"),
                        eq(Boolean.class),
                        eq("agriinsight_alert_worker"));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void rejectsUnavailableSchemaHistoryWithoutLeakingDatabaseDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("FROM public.flyway_schema_history"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION)))
                .thenThrow(new DataAccessResourceFailureException("database password leaked in exception"));

        assertThatThrownBy(() -> verifier(jdbcTemplate).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker schema verification failed");
    }

    @Test
    void rejectsDangerousRoleOrPrivilegeDriftWithAnEvidenceBackedCatalogQuery() throws Exception {
        VerificationHarness harness = verifier(false, false);

        assertThatThrownBy(harness.verifier()::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operational alert worker database role verification failed");

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(harness.jdbcTemplate())
                .queryForObject(
                        query.capture(), eq(Boolean.class), eq("agriinsight_alert_worker"));
        assertThat(query.getValue())
                .contains(
                        "session_user = current_user",
                        "has_schema_privilege",
                        "worker_role.rolcanlogin",
                        "NOT worker_role.rolsuper",
                        "NOT worker_role.rolinherit",
                        "NOT worker_role.rolcreaterole",
                        "NOT worker_role.rolcreatedb",
                        "NOT worker_role.rolreplication",
                        "NOT worker_role.rolbypassrls",
                        "pg_catalog.pg_auth_members",
                        "relation.relname <> ALL",
                        "has_table_privilege",
                        "has_any_column_privilege",
                        "allowed_metadata_column",
                        "'outbox_events'::NAME, 'id'::NAME",
                        "'flyway_schema_history'::NAME, 'installed_rank'::NAME",
                        "allowed_worker_state_update_column",
                        "required_worker_policy",
                        "relation.relrowsecurity",
                        "relation.relforcerowsecurity",
                        "pg_catalog.pg_get_expr")
                .doesNotContain("'payload'::NAME", "'last_error'::NAME");
    }

    @SuppressWarnings("unchecked")
    private static VerificationHarness verifier(boolean invalidSourceEvidence) throws Exception {
        return verifier(invalidSourceEvidence, true);
    }

    @SuppressWarnings("unchecked")
    private static VerificationHarness verifier(boolean invalidSourceEvidence, boolean roleVerified)
            throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("WHERE version = ?"),
                        eq(Boolean.class),
                        eq(EXPECTED_SCHEMA_VERSION)))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                        contains("ORDER BY installed_rank DESC"),
                        eq(Boolean.class),
                        eq("R__tenant_rls_helpers_and_grants.sql")))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                        contains("current_user = CAST"),
                        eq(Boolean.class),
                        eq("agriinsight_alert_worker")))
                .thenReturn(roleVerified);

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
                        jdbcTemplate,
                        workerProperties(),
                        alertProperties(),
                        kafkaProperties()),
                jdbcTemplate,
                connection,
                statement);
    }

    private static RealtimeWorkerRoleVerifier verifier(JdbcTemplate jdbcTemplate) {
        return new RealtimeWorkerRoleVerifier(
                jdbcTemplate,
                workerProperties(),
                alertProperties(),
                kafkaProperties());
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
            RealtimeWorkerRoleVerifier verifier,
            JdbcTemplate jdbcTemplate,
            Connection connection,
            PreparedStatement statement) {
    }
}
