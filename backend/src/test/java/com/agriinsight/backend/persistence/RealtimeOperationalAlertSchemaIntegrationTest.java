package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.alertWorkerConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.realtimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeOperationalAlertSchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void alertProjectionUsesForceRlsAndMinimalRuntimeAndWorkerGrants() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_class relation
                      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relname IN (
                           'realtime_operational_alerts',
                           'realtime_alert_acknowledgement_revisions',
                           'realtime_operational_alert_scan_cursors')
                       AND relation.relrowsecurity
                       AND relation.relforcerowsecurity
                    """)).isEqualTo(3);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_operational_alerts', TRUE, FALSE, FALSE, FALSE),
                              ('public.realtime_alert_acknowledgement_revisions', TRUE, TRUE, FALSE, FALSE)
                      ) AS scoped(table_name, select_allowed, insert_allowed, update_allowed, delete_allowed)
                     WHERE has_table_privilege('agriinsight_runtime', table_name, 'SELECT') = select_allowed
                       AND has_table_privilege('agriinsight_runtime', table_name, 'INSERT') = insert_allowed
                       AND has_table_privilege('agriinsight_runtime', table_name, 'UPDATE') = update_allowed
                       AND has_table_privilege('agriinsight_runtime', table_name, 'DELETE') = delete_allowed
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_operational_alerts', FALSE, FALSE, FALSE, FALSE),
                              ('public.realtime_alert_acknowledgement_revisions', FALSE, FALSE, FALSE, FALSE),
                              ('public.realtime_operational_alert_scan_cursors', FALSE, FALSE, FALSE, FALSE)
                      ) AS scoped(table_name, select_allowed, insert_allowed, update_allowed, delete_allowed)
                     WHERE has_table_privilege('agriinsight_realtime', table_name, 'SELECT') = select_allowed
                       AND has_table_privilege('agriinsight_realtime', table_name, 'INSERT') = insert_allowed
                       AND has_table_privilege('agriinsight_realtime', table_name, 'UPDATE') = update_allowed
                       AND has_table_privilege('agriinsight_realtime', table_name, 'DELETE') = delete_allowed
                    """)).isEqualTo(3);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_operational_alerts', TRUE, TRUE, FALSE),
                              ('public.realtime_operational_alert_scan_cursors', TRUE, TRUE, TRUE)
                      ) AS scoped(table_name, select_allowed, insert_allowed, delete_allowed)
                     WHERE has_table_privilege('agriinsight_alert_worker', table_name, 'SELECT') = select_allowed
                       AND has_table_privilege('agriinsight_alert_worker', table_name, 'INSERT') = insert_allowed
                       AND has_table_privilege('agriinsight_alert_worker', table_name, 'DELETE') = delete_allowed
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_operational_alerts', 'severity', TRUE),
                              ('public.realtime_operational_alerts', 'dedupe_key', FALSE),
                              ('public.realtime_operational_alert_scan_cursors', 'cursor_tenant_id', TRUE),
                              ('public.realtime_operational_alert_scan_cursors', 'policy_code', FALSE)
                      ) AS scoped(table_name, column_name, update_allowed)
                     WHERE has_column_privilege(
                               'agriinsight_alert_worker', table_name, column_name, 'UPDATE') = update_allowed
                    """)).isEqualTo(4);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM role_permissions
                     WHERE (role_code, permission_code) IN (
                         ('TENANT_ADMIN', 'REALTIME_ALERT_READ'),
                         ('TENANT_ADMIN', 'REALTIME_ALERT_ACKNOWLEDGE'),
                         ('EXECUTIVE', 'REALTIME_ALERT_READ'),
                         ('EXECUTIVE', 'REALTIME_ALERT_ACKNOWLEDGE'),
                         ('DATA_ANALYST', 'REALTIME_ALERT_READ'),
                         ('DATA_ANALYST', 'REALTIME_ALERT_ACKNOWLEDGE'))
                    """)).isEqualTo(6);
        }
    }

    @Test
    void runtimeAcknowledgementsRemainTenantAndProfileScopedAndResetWithTransaction() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (
                        '41000000-0000-0000-0000-000000000099',
                        '10000000-0000-0000-0000-000000000041',
                        'Another operator');
                    """);
        }
        try (var alertWorker = alertWorkerConnection(POSTGRESQL, "agriinsight")) {
            execute(alertWorker, alertInsert(
                    "61000000-0000-0000-0000-000000000001",
                    "10000000-0000-0000-0000-000000000041",
                    'a'));
            execute(alertWorker, alertInsert(
                    "62000000-0000-0000-0000-000000000001",
                    "10000000-0000-0000-0000-000000000042",
                    'b'));
        }

        try (var realtime = realtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThatThrownBy(() -> count(realtime, "SELECT count(*) FROM realtime_operational_alerts"))
                    .isInstanceOf(SQLException.class);
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM realtime_operational_alerts")).isEqualTo(1);
            execute(runtime, """
                    INSERT INTO realtime_alert_acknowledgement_revisions (
                        id, tenant_id, alert_id, profile_id, acknowledged_observation_at)
                    VALUES (
                        '71000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        '61000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000005',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z');
                    """);
            assertThat(count(runtime, "SELECT count(*) FROM realtime_alert_acknowledgement_revisions"))
                    .isEqualTo(1);
            runtime.rollback();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThatThrownBy(() -> execute(runtime, """
                    INSERT INTO realtime_alert_acknowledgement_revisions (
                        id, tenant_id, alert_id, profile_id, acknowledged_observation_at)
                    VALUES (
                        '71000000-0000-0000-0000-000000000099',
                        '10000000-0000-0000-0000-000000000041',
                        '61000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000005',
                        TIMESTAMPTZ '2027-09-01T00:01:00Z')
                    """)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThatThrownBy(() -> execute(runtime, """
                    INSERT INTO realtime_alert_acknowledgement_revisions (
                        id, tenant_id, alert_id, profile_id, acknowledged_observation_at)
                    VALUES (
                        '71000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000041',
                        '61000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000099',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z')
                    """)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThatThrownBy(() -> execute(runtime, """
                    UPDATE realtime_operational_alerts
                       SET severity = 'CRITICAL'
                     WHERE id = '61000000-0000-0000-0000-000000000001'
                    """)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }

        try (var runtimeWithoutContext = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtimeWithoutContext.setAutoCommit(false);
            execute(runtimeWithoutContext, """
                    SELECT set_config('app.tenant_id', '10000000-0000-0000-0000-000000000041', true),
                           set_config('app.profile_id', '41000000-0000-0000-0000-000000000005', true)
                    """);
            assertThat(count(runtimeWithoutContext, "SELECT count(*) FROM realtime_operational_alerts"))
                    .isEqualTo(1);
            runtimeWithoutContext.rollback();

            assertThat(count(runtimeWithoutContext, "SELECT count(*) FROM realtime_operational_alerts"))
                    .isZero();
            assertThat(count(runtimeWithoutContext, "SELECT count(*) FROM realtime_alert_acknowledgement_revisions"))
                    .isZero();
        }
    }

    private static String alertInsert(String id, String tenantId, char dedupeFill) {
        return """
                INSERT INTO realtime_operational_alerts (
                    id, tenant_id, policy_code, dedupe_key, severity, state,
                    source_occurred_at, opened_at, last_observed_at, last_evaluated_at)
                VALUES ('%s', '%s', 'OUTBOX_PUBLISH_BACKLOG', repeat('%s', 64), 'WARNING', 'OPEN',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z',
                        TIMESTAMPTZ '2027-09-01T00:00:00Z');
                """.formatted(id, tenantId, dedupeFill);
    }
}
