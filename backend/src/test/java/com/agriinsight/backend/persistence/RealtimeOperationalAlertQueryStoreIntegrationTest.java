package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertAcknowledgementStore;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertFeed;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeOperationalAlertQueryStore;
import com.agriinsight.backend.shared.application.CommandExecutionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeOperationalAlertQueryStoreIntegrationTest {

    private static final UUID PROFILE_A =
            UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID PROFILE_A_SECOND =
            UUID.fromString("41900000-0000-0000-0000-000000000005");
    private static final UUID TENANT_B =
            UUID.fromString("10000000-0000-0000-0000-000000000042");
    private static final UUID PROFILE_B =
            UUID.fromString("42000000-0000-0000-0000-000000000005");
    private static final UUID PROFILE_ALERT_ID =
            UUID.fromString("79400000-0000-0000-0000-000000000001");
    private static final Instant GENERATED_AT = Instant.parse("2042-01-01T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        seedQueryFixtures();
    }

    @Test
    void runtimeRolePreventsTenantAndCurrentProfileOverrides() {
        JdbcTemplate runtime = runtimeJdbcTemplate();
        TransactionTemplate runtimeTransaction = transaction(runtime);
        PostgresRealtimeOperationalAlertQueryStore queries =
                new PostgresRealtimeOperationalAlertQueryStore(runtime);

        RealtimeOperationalAlertView profileAView = runtimeTransaction.execute(status -> {
            bindRuntimeScope(runtime, TENANT_A, PROFILE_A);
            return queries.findOpenById(TENANT_A, PROFILE_A, PROFILE_ALERT_ID, GENERATED_AT)
                    .orElseThrow();
        });
        RealtimeOperationalAlertView secondProfileView = runtimeTransaction.execute(status -> {
            bindRuntimeScope(runtime, TENANT_A, PROFILE_A_SECOND);
            return queries.findOpenById(
                            TENANT_A, PROFILE_A_SECOND, PROFILE_ALERT_ID, GENERATED_AT)
                    .orElseThrow();
        });
        RealtimeOperationalAlertView attemptedProfileOverride =
                runtimeTransaction.execute(status -> {
                    bindRuntimeScope(runtime, TENANT_A, PROFILE_A_SECOND);
                    return queries.findOpenById(
                                    TENANT_A, PROFILE_A, PROFILE_ALERT_ID, GENERATED_AT)
                            .orElseThrow();
                });
        List<RealtimeOperationalAlertView> attemptedTenantOverride =
                runtimeTransaction.execute(status -> {
                    bindRuntimeScope(runtime, TENANT_A, PROFILE_A);
                    return queries.findLatestOpen(TENANT_B, PROFILE_B, GENERATED_AT);
                });

        assertThat(profileAView.acknowledged()).isTrue();
        assertThat(profileAView.acknowledgedAt())
                .contains(Instant.parse("2038-01-01T00:02:00Z"));
        assertThat(secondProfileView.acknowledged()).isFalse();
        assertThat(secondProfileView.acknowledgedAt()).isEmpty();
        assertThat(attemptedProfileOverride.acknowledged()).isFalse();
        assertThat(attemptedProfileOverride.acknowledgedAt()).isEmpty();
        assertThat(attemptedTenantOverride).isEmpty();
    }

    @Test
    void ordersCriticalBeforeWarningThenNewestObservationThenUuid() {
        List<UUID> expectedIds = List.of(
                UUID.fromString("79200000-0000-0000-0000-000000000001"),
                UUID.fromString("79200000-0000-0000-0000-000000000002"),
                UUID.fromString("79200000-0000-0000-0000-000000000003"),
                UUID.fromString("79200000-0000-0000-0000-000000000004"));
        JdbcTemplate runtime = runtimeJdbcTemplate();
        PostgresRealtimeOperationalAlertQueryStore queries =
                new PostgresRealtimeOperationalAlertQueryStore(runtime);

        List<RealtimeOperationalAlertView> rows = transaction(runtime).execute(status -> {
            bindRuntimeScope(runtime, TENANT_A, PROFILE_A);
            return queries.findLatestOpen(TENANT_A, PROFILE_A, GENERATED_AT);
        });

        assertThat(rows)
                .filteredOn(view -> expectedIds.contains(view.id()))
                .extracting(RealtimeOperationalAlertView::id)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void serviceUsesTheRealFiftyOneRowLookaheadToSetHasMore() {
        JdbcTemplate runtime = runtimeJdbcTemplate();
        PostgresRealtimeOperationalAlertQueryStore queries =
                new PostgresRealtimeOperationalAlertQueryStore(runtime);
        PermissionEvaluator permissions = mock(PermissionEvaluator.class);
        ScopeContext scope = new ScopeContext(
                TENANT_A, PROFILE_A, ScopeContext.Type.TENANT, Optional.empty());
        when(permissions.requireTenant(Permission.REALTIME_ALERT_READ)).thenReturn(scope);
        RealtimeOperationalAlertService service = new RealtimeOperationalAlertService(
                permissions,
                queries,
                mock(RealtimeOperationalAlertAcknowledgementStore.class),
                mock(CommandExecutionService.class));

        RealtimeOperationalAlertFeed feed = transaction(runtime).execute(status -> {
            bindRuntimeScope(runtime, TENANT_A, PROFILE_A);
            assertThat(queries.findLatestOpen(TENANT_A, PROFILE_A, Instant.now()))
                    .hasSize(51);
            return service.feed();
        });

        assertThat(feed.limit()).isEqualTo(50);
        assertThat(feed.items()).hasSize(50);
        assertThat(feed.hasMore()).isTrue();
    }

    private static void seedQueryFixtures() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (
                        '41900000-0000-0000-0000-000000000005',
                        '10000000-0000-0000-0000-000000000041',
                        'Second alert reader A')
                    """);
            execute(operator, isolationAlerts());
            execute(operator, orderingAlerts());
            execute(operator, lookaheadAlerts());
            execute(operator, """
                    INSERT INTO realtime_alert_acknowledgement_revisions (
                        id, tenant_id, alert_id, profile_id,
                        acknowledged_observation_at, acknowledged_at)
                    VALUES (
                        '79700000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        '79400000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000005',
                        TIMESTAMPTZ '2038-01-01T00:01:00Z',
                        TIMESTAMPTZ '2038-01-01T00:02:00Z')
                    """);
        }
    }

    private static String isolationAlerts() {
        return """
                INSERT INTO realtime_operational_alerts (
                    id, tenant_id, policy_code, dedupe_key, severity, state,
                    source_event_id, source_occurred_at, opened_at, last_observed_at,
                    last_evaluated_at, version)
                VALUES
                    (
                        '79400000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        'REALTIME_DLT_RECORD', repeat('e', 64), 'WARNING', 'OPEN',
                        '79500000-0000-0000-0000-000000000001',
                        TIMESTAMPTZ '2038-01-01T00:00:00Z',
                        TIMESTAMPTZ '2038-01-01T00:00:00Z',
                        TIMESTAMPTZ '2038-01-01T00:01:00Z',
                        TIMESTAMPTZ '2038-01-01T00:01:00Z', 1
                    ),
                    (
                        '79600000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000042',
                        'REALTIME_DLT_RECORD', repeat('f', 64), 'CRITICAL', 'OPEN',
                        '79600000-0000-0000-0000-000000000002',
                        TIMESTAMPTZ '2038-01-01T00:00:00Z',
                        TIMESTAMPTZ '2038-01-01T00:00:00Z',
                        TIMESTAMPTZ '2038-01-01T00:01:00Z',
                        TIMESTAMPTZ '2038-01-01T00:01:00Z', 1
                    )
                """;
    }

    private static String orderingAlerts() {
        return """
                INSERT INTO realtime_operational_alerts (
                    id, tenant_id, policy_code, dedupe_key, severity, state,
                    source_event_id, source_occurred_at, opened_at, last_observed_at,
                    last_evaluated_at, version)
                SELECT (
                           '79200000-0000-0000-0000-'
                           || lpad(series::text, 12, '0')
                       )::uuid,
                       '10000000-0000-0000-0000-000000000041',
                       'REALTIME_DELIVERY_LAG',
                       repeat(series::text, 64),
                       CASE WHEN series <= 3 THEN 'CRITICAL' ELSE 'WARNING' END,
                       'OPEN',
                       (
                           '79300000-0000-0000-0000-'
                           || lpad(series::text, 12, '0')
                       )::uuid,
                       TIMESTAMPTZ '2039-01-01T00:00:00Z',
                       TIMESTAMPTZ '2039-01-01T00:00:00Z',
                       CASE
                           WHEN series <= 2 THEN TIMESTAMPTZ '2040-01-02T00:00:00Z'
                           WHEN series = 3 THEN TIMESTAMPTZ '2040-01-01T00:00:00Z'
                           ELSE TIMESTAMPTZ '2041-01-01T00:00:00Z'
                       END,
                       CASE
                           WHEN series <= 2 THEN TIMESTAMPTZ '2040-01-02T00:00:00Z'
                           WHEN series = 3 THEN TIMESTAMPTZ '2040-01-01T00:00:00Z'
                           ELSE TIMESTAMPTZ '2041-01-01T00:00:00Z'
                       END,
                       1
                  FROM generate_series(1, 4) AS series
                """;
    }

    private static String lookaheadAlerts() {
        return """
                INSERT INTO realtime_operational_alerts (
                    id, tenant_id, policy_code, dedupe_key, severity, state,
                    source_event_id, source_occurred_at, opened_at, last_observed_at,
                    last_evaluated_at, version)
                SELECT (
                           '79000000-0000-0000-0000-'
                           || lpad(series::text, 12, '0')
                       )::uuid,
                       '10000000-0000-0000-0000-000000000041',
                       'REALTIME_DLT_RECORD',
                       md5(series::text) || md5('feed-' || series::text),
                       'WARNING',
                       'OPEN',
                       (
                           '79100000-0000-0000-0000-'
                           || lpad(series::text, 12, '0')
                       )::uuid,
                       TIMESTAMPTZ '2029-01-01T00:00:00Z'
                           + make_interval(secs => series),
                       TIMESTAMPTZ '2029-01-01T00:00:00Z'
                           + make_interval(secs => series),
                       TIMESTAMPTZ '2030-01-01T00:00:00Z'
                           + make_interval(secs => series),
                       TIMESTAMPTZ '2030-01-01T00:00:00Z'
                           + make_interval(secs => series),
                       1
                  FROM generate_series(1, 46) AS series
                """;
    }

    private static JdbcTemplate runtimeJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"),
                PostgresIntegrationSupport.RUNTIME,
                PostgresIntegrationSupport.RUNTIME_PASSWORD);
        return new JdbcTemplate(dataSource);
    }

    private static TransactionTemplate transaction(JdbcTemplate jdbcTemplate) {
        return new TransactionTemplate(new DataSourceTransactionManager(
                (DriverManagerDataSource) jdbcTemplate.getDataSource()));
    }

    private static void bindRuntimeScope(
            JdbcTemplate runtime,
            UUID tenantId,
            UUID profileId) {
        runtime.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class,
                tenantId.toString());
        runtime.queryForObject(
                "SELECT set_config('app.profile_id', ?, true)",
                String.class,
                profileId.toString());
    }
}
