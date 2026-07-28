package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.realtimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeReadModelSchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void readModelsKeepRawPayloadsOutAndGrantOnlyBoundedAccess() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM pg_class relation
                      JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                     WHERE namespace.nspname = 'public'
                       AND relation.relname IN (
                           'realtime_event_receipts',
                           'realtime_aggregate_progress',
                           'realtime_tenant_metrics')
                       AND relation.relrowsecurity
                       AND relation.relforcerowsecurity
                    """)).isEqualTo(3);
            assertThat(scalar(operator, """
                    SELECT string_agg(
                        format('%s.%s', table_name, column_name),
                        ',' ORDER BY table_name, ordinal_position)
                      FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name IN (
                           'realtime_event_receipts',
                           'realtime_aggregate_progress',
                           'realtime_tenant_metrics')
                    """)).isEqualTo(
                    "realtime_aggregate_progress.tenant_id,"
                            + "realtime_aggregate_progress.aggregate_type,"
                            + "realtime_aggregate_progress.aggregate_id,"
                            + "realtime_aggregate_progress.last_version,"
                            + "realtime_aggregate_progress.last_event_id,"
                            + "realtime_aggregate_progress.updated_at,"
                            + "realtime_event_receipts.event_id,"
                            + "realtime_event_receipts.tenant_id,"
                            + "realtime_event_receipts.checksum,"
                            + "realtime_event_receipts.topic,"
                            + "realtime_event_receipts.partition_id,"
                            + "realtime_event_receipts.broker_offset,"
                            + "realtime_event_receipts.received_at,"
                            + "realtime_tenant_metrics.tenant_id,"
                            + "realtime_tenant_metrics.event_type,"
                            + "realtime_tenant_metrics.aggregate_type,"
                            + "realtime_tenant_metrics.event_count,"
                            + "realtime_tenant_metrics.last_occurred_at,"
                            + "realtime_tenant_metrics.last_processed_at");
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_event_receipts', FALSE),
                              ('public.realtime_aggregate_progress', FALSE),
                              ('public.realtime_tenant_metrics', TRUE)
                      ) AS scoped(table_name, expected_select)
                     WHERE has_table_privilege(
                               'agriinsight_runtime', table_name, 'SELECT') = expected_select
                       AND NOT has_table_privilege(
                               'agriinsight_runtime', table_name, 'INSERT')
                       AND NOT has_table_privilege(
                               'agriinsight_runtime', table_name, 'UPDATE')
                       AND NOT has_table_privilege(
                               'agriinsight_runtime', table_name, 'DELETE')
                    """)).isEqualTo(3);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_event_receipts'),
                              ('public.realtime_aggregate_progress'),
                              ('public.realtime_tenant_metrics')
                      ) AS scoped(table_name)
                     WHERE has_table_privilege(
                               'agriinsight_realtime', table_name, 'SELECT')
                       AND has_table_privilege(
                               'agriinsight_realtime', table_name, 'INSERT')
                       AND NOT has_table_privilege(
                               'agriinsight_realtime', table_name, 'UPDATE')
                       AND NOT has_table_privilege(
                               'agriinsight_realtime', table_name, 'DELETE')
                    """)).isEqualTo(3);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (
                          VALUES
                              ('public.realtime_event_receipts', 'event_id', FALSE),
                              ('public.realtime_event_receipts', 'tenant_id', FALSE),
                              ('public.realtime_event_receipts', 'checksum', FALSE),
                              ('public.realtime_event_receipts', 'topic', FALSE),
                              ('public.realtime_event_receipts', 'partition_id', FALSE),
                              ('public.realtime_event_receipts', 'broker_offset', FALSE),
                              ('public.realtime_event_receipts', 'received_at', FALSE),
                              ('public.realtime_aggregate_progress', 'tenant_id', FALSE),
                              ('public.realtime_aggregate_progress', 'aggregate_type', FALSE),
                              ('public.realtime_aggregate_progress', 'aggregate_id', FALSE),
                              ('public.realtime_aggregate_progress', 'last_version', TRUE),
                              ('public.realtime_aggregate_progress', 'last_event_id', TRUE),
                              ('public.realtime_aggregate_progress', 'updated_at', TRUE),
                              ('public.realtime_tenant_metrics', 'tenant_id', FALSE),
                              ('public.realtime_tenant_metrics', 'event_type', FALSE),
                              ('public.realtime_tenant_metrics', 'aggregate_type', FALSE),
                              ('public.realtime_tenant_metrics', 'event_count', TRUE),
                              ('public.realtime_tenant_metrics', 'last_occurred_at', TRUE),
                              ('public.realtime_tenant_metrics', 'last_processed_at', TRUE)
                      ) AS scoped(table_name, column_name, expected_update)
                     WHERE has_column_privilege(
                               'agriinsight_realtime', table_name, column_name, 'UPDATE')
                           = expected_update
                    """)).isEqualTo(19);
        }
    }

    @Test
    void runtimeReadsOnlyItsTenantSummaryWhileRealtimeWritesAcrossTenants() throws Exception {
        try (var realtime = realtimeConnection(POSTGRESQL, "agriinsight")) {
            execute(realtime, """
                    INSERT INTO realtime_event_receipts (
                        event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                    VALUES (
                        '51000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        repeat('a', 64), 'agriinsight.operational.v1', 0, 1);
                    INSERT INTO realtime_aggregate_progress (
                        tenant_id, aggregate_type, aggregate_id, last_version, last_event_id)
                    VALUES (
                        '10000000-0000-0000-0000-000000000041',
                        'ACTIVITY', '41000000-0000-0000-0000-000000000007', 1,
                        '51000000-0000-0000-0000-000000000001');
                    """);
            assertThatThrownBy(() -> execute(realtime, """
                    INSERT INTO realtime_aggregate_progress (
                        tenant_id, aggregate_type, aggregate_id, last_version, last_event_id)
                    VALUES (
                        '10000000-0000-0000-0000-000000000042',
                        'ACTIVITY', '42000000-0000-0000-0000-000000000007', 1,
                        '51000000-0000-0000-0000-000000000001')
                    """)).isInstanceOf(SQLException.class);
            execute(realtime, """
                    INSERT INTO realtime_tenant_metrics (
                        tenant_id, event_type, aggregate_type, event_count,
                        last_occurred_at, last_processed_at)
                    VALUES
                        (
                            '10000000-0000-0000-0000-000000000041',
                            'AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED',
                            'ACTIVITY', 2,
                            TIMESTAMPTZ '2027-09-01T02:00:00Z',
                            TIMESTAMPTZ '2027-09-01T02:01:00Z'
                        ),
                        (
                            '10000000-0000-0000-0000-000000000042',
                            'AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED',
                            'HARVEST', 3,
                            TIMESTAMPTZ '2027-09-02T02:00:00Z',
                            TIMESTAMPTZ '2027-09-02T02:01:00Z'
                        )
                    """);
            assertThatThrownBy(() -> execute(realtime, """
                    INSERT INTO realtime_event_receipts (
                        event_id, tenant_id, checksum, topic, partition_id, broker_offset)
                    VALUES (
                        '51000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000042',
                        repeat('b', 64), 'agriinsight.operational.v1', 0, 1)
                    """)).isInstanceOf(SQLException.class);
            execute(realtime, """
                    UPDATE realtime_aggregate_progress
                       SET last_version = 2, updated_at = clock_timestamp()
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041';
                    UPDATE realtime_tenant_metrics
                       SET event_count = 3, last_processed_at = clock_timestamp()
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                    """);
            assertThatThrownBy(() -> execute(realtime, """
                    UPDATE realtime_event_receipts
                       SET checksum = repeat('c', 64)
                     WHERE event_id = '51000000-0000-0000-0000-000000000001'
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(realtime, """
                    UPDATE realtime_aggregate_progress
                       SET aggregate_type = 'HARVEST'
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(realtime, """
                    UPDATE realtime_tenant_metrics
                       SET event_type = 'AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED'
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                    """)).isInstanceOf(SQLException.class);
        }

        try (var runtimeWithoutContext = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(runtimeWithoutContext, "SELECT count(*) FROM realtime_tenant_metrics"))
                    .isZero();
        }

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(runtime, "SELECT count(*) FROM realtime_tenant_metrics")).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*)
                      FROM realtime_tenant_metrics
                     WHERE event_type = 'AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED'
                    """)).isZero();
            assertThatThrownBy(() -> count(runtime, "SELECT count(*) FROM realtime_event_receipts"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(runtime, """
                    INSERT INTO realtime_tenant_metrics (
                        tenant_id, event_type, aggregate_type, event_count,
                        last_occurred_at, last_processed_at)
                    VALUES (
                        '10000000-0000-0000-0000-000000000041',
                        'AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED',
                        'ACTIVITY', 1, clock_timestamp(), clock_timestamp())
                    """)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }
    }
}
