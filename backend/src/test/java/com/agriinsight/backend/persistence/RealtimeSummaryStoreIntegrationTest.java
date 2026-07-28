package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.realtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeSummaryStore;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeSummaryStoreIntegrationTest {

    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runtimeReaderGetsOnlyStableBoundedMetricsForItsTenant() throws Throwable {
        seedMetrics();
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal);

        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresRealtimeSummaryStore store = new PostgresRealtimeSummaryStore(harness.jdbcTemplate());
            var summary = harness.withinTenant(() -> store.summarize(ScopeContext.tenant(principal)));

            assertThat(summary.tenantId()).isEqualTo(TENANT_A);
            assertThat(summary.eventCount()).isEqualTo(7);
            assertThat(summary.freshnessSeconds()).isGreaterThanOrEqualTo(0);
            assertThat(summary.lastOccurredAt()).contains(java.time.Instant.parse("2027-09-01T02:05:00Z"));
            assertThat(summary.lastProcessedAt()).contains(java.time.Instant.parse("2027-09-01T02:11:00Z"));
            assertThat(summary.items()).extracting(item -> item.eventType())
                    .containsExactly(
                            "AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED",
                            "AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED");
            assertThat(summary.items()).extracting(item -> item.eventCount()).containsExactly(3L, 4L);
        }
    }

    @Test
    void summaryOrderingUsesTheTenantLeadingMetricIndex() throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO realtime_tenant_metrics (
                        tenant_id, event_type, aggregate_type, event_count,
                        last_occurred_at, last_processed_at)
                    SELECT '10000000-0000-0000-0000-000000000042'::uuid,
                           'AGRIINSIGHT.OPERATIONAL.FARM' || lpad(series::text, 3, '0') || '.COMMITTED',
                           'FARM' || lpad(series::text, 3, '0'), series,
                           TIMESTAMPTZ '2027-09-01T00:00:00Z',
                           TIMESTAMPTZ '2027-09-01T00:00:00Z' + make_interval(secs => series)
                      FROM generate_series(1, 150) AS series
                    ON CONFLICT (tenant_id, event_type) DO NOTHING
                    """);
            execute(operator, "SET enable_seqscan = off");

            assertThat(explain(operator, """
                    SELECT event_type, aggregate_type, event_count, last_occurred_at, last_processed_at
                      FROM realtime_tenant_metrics
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000042'
                     ORDER BY last_processed_at DESC, event_type ASC
                     LIMIT 100
                    """)).anyMatch(line -> line.contains("ix_realtime_tenant_metrics_summary"));
        }
    }

    private static void seedMetrics() throws Exception {
        try (Connection realtime = realtimeConnection(POSTGRESQL, "agriinsight")) {
            execute(realtime, """
                    INSERT INTO realtime_tenant_metrics (
                        tenant_id, event_type, aggregate_type, event_count,
                        last_occurred_at, last_processed_at)
                    VALUES
                        ('10000000-0000-0000-0000-000000000041',
                         'AGRIINSIGHT.OPERATIONAL.ACTIVITY.COMMITTED', 'ACTIVITY', 4,
                         TIMESTAMPTZ '2027-09-01T02:00:00Z', TIMESTAMPTZ '2027-09-01T02:10:00Z'),
                        ('10000000-0000-0000-0000-000000000041',
                         'AGRIINSIGHT.OPERATIONAL.HARVEST.COMMITTED', 'HARVEST', 3,
                         TIMESTAMPTZ '2027-09-01T02:05:00Z', TIMESTAMPTZ '2027-09-01T02:11:00Z'),
                        ('10000000-0000-0000-0000-000000000042',
                         'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 'FARM', 8,
                         TIMESTAMPTZ '2027-09-02T02:00:00Z', TIMESTAMPTZ '2027-09-02T02:01:00Z')
                    ON CONFLICT (tenant_id, event_type) DO NOTHING
                    """);
        }
    }

    private static List<String> explain(Connection connection, String query) throws Exception {
        List<String> plan = new ArrayList<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("EXPLAIN (COSTS OFF) " + query)) {
            while (rows.next()) {
                plan.add(rows.getString(1));
            }
        }
        return plan;
    }

    private static void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_DATA_ANALYST"))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_A; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
