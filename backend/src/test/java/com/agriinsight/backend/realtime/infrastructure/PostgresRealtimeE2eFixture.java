package com.agriinsight.backend.realtime.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.REALTIME;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.REALTIME_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.jdbcUrl;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.integration.domain.OutboxStatus;
import com.agriinsight.backend.integration.infrastructure.PostgresOutboxWriter;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.application.CommandCommittedEvent;
import com.agriinsight.backend.shared.application.CommandTarget;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

final class PostgresRealtimeE2eFixture implements AutoCloseable {

    static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000041");
    static final UUID TENANT_B = UUID.fromString("10000000-0000-0000-0000-000000000042");
    static final UUID PROFILE_A = UUID.fromString("41000000-0000-0000-0000-000000000005");
    static final UUID PROFILE_B = UUID.fromString("42000000-0000-0000-0000-000000000005");
    static final UUID AGGREGATE_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    static final UUID FIRST_COMMAND_ID = UUID.fromString("77000000-0000-0000-0000-000000000101");
    static final UUID SECOND_COMMAND_ID = UUID.fromString("77000000-0000-0000-0000-000000000102");

    private static boolean prepared;

    private final HikariDataSource realtimeDataSource;
    private final TenantTransactionTestHarness runtime;
    private final JdbcTemplate realtimeJdbc;
    private final PostgresOutboxWriter outboxWriter;

    private PostgresRealtimeE2eFixture(
            HikariDataSource realtimeDataSource,
            TenantTransactionTestHarness runtime) {
        this.realtimeDataSource = realtimeDataSource;
        this.runtime = runtime;
        this.realtimeJdbc = new JdbcTemplate(realtimeDataSource);
        this.outboxWriter = new PostgresOutboxWriter(runtime.jdbcTemplate(), new JsonMapper());
    }

    static synchronized void prepare(PostgreSQLContainer postgresql) throws Exception {
        if (prepared) {
            return;
        }
        migrateAndSeed(postgresql);
        prepared = true;
    }

    static PostgresRealtimeE2eFixture create(PostgreSQLContainer postgresql) throws Exception {
        prepare(postgresql);
        return new PostgresRealtimeE2eFixture(realtimeDataSource(postgresql),
                TenantTransactionTestHarness.runtime(postgresql, "agriinsight"));
    }

    OutboxEvent append(UUID commandId, long aggregateVersion) throws Throwable {
        return append(commandId, AGGREGATE_ID, aggregateVersion);
    }

    OutboxEvent append(UUID commandId, UUID aggregateId, long aggregateVersion) throws Throwable {
        return asTenant(TENANT_A, PROFILE_A, () -> {
            runtime.withinTenant(() -> {
                insertCommandRecord(commandId);
                outboxWriter.append(new CommandCommittedEvent(
                        TENANT_A, PROFILE_A, commandId, "/api/v1/farms",
                        new CommandTarget("FARM", aggregateId, aggregateVersion),
                        Optional.of("realtime-e2e"), Instant.now(), 0));
                return null;
            });
            return outbox(commandId);
        });
    }

    OutboxEvent outbox(UUID commandId) {
        return realtimeJdbc.queryForObject("SELECT * FROM outbox_events WHERE command_id = ?", (row, ignored) ->
                new OutboxEvent(
                        row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                        row.getObject("command_id", UUID.class), row.getInt("event_ordinal"),
                        row.getString("aggregate_type"), row.getObject("aggregate_id", UUID.class),
                        row.getLong("aggregate_version"), row.getString("event_type"),
                        row.getInt("schema_version"), row.getTimestamp("occurred_at").toInstant(),
                        row.getString("payload"), OutboxStatus.valueOf(row.getString("status")),
                        row.getInt("attempts"), row.getInt("max_attempts"),
                        row.getTimestamp("available_at").toInstant(), instant(row.getTimestamp("leased_until")),
                        instant(row.getTimestamp("published_at")), instant(row.getTimestamp("dead_lettered_at")),
                        Optional.ofNullable(row.getString("lease_owner")),
                        Optional.ofNullable(row.getObject("lease_token", UUID.class)), row.getLong("lease_generation"),
                        Optional.ofNullable(row.getString("last_error"))), commandId);
    }

    long aggregateVersion() {
        return realtimeJdbc.queryForObject(
                "SELECT last_version FROM realtime_aggregate_progress WHERE tenant_id = ? AND aggregate_id = ?",
                Long.class, TENANT_A, AGGREGATE_ID);
    }

    @Override
    public void close() {
        SecurityContextHolder.clearContext();
        runtime.close();
        realtimeDataSource.close();
    }

    private static HikariDataSource realtimeDataSource(PostgreSQLContainer postgresql) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl(postgresql, "agriinsight"));
        configuration.setUsername(REALTIME);
        configuration.setPassword(REALTIME_PASSWORD);
        configuration.setMaximumPoolSize(4);
        configuration.setMinimumIdle(1);
        return new HikariDataSource(configuration);
    }

    private static Optional<Instant> instant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    private void insertCommandRecord(UUID commandId) {
        String hexadecimalIdentifier = commandId.toString().replace("-", "");
        String deterministicDigest = hexadecimalIdentifier + hexadecimalIdentifier;
        runtime.jdbcTemplate().update("""
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                VALUES (?, ?, ?, 'POST', '/api/v1/farms', ?, 1, ?, 'IN_PROGRESS')
                """, commandId, TENANT_A, PROFILE_A, deterministicDigest, deterministicDigest);
    }

    private static <T> T asTenant(UUID tenant, UUID profile, ThrowingOperation<T> operation) throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new TestPrincipal(tenant, profile), null, java.util.List.of()));
        try {
            return operation.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record TestPrincipal(UUID tenantId, UUID profileId) implements TenantPrincipal {
        @Override public String getName() { return profileId.toString(); }
    }

    @FunctionalInterface
    private interface ThrowingOperation<T> {
        T get() throws Throwable;
    }
}
