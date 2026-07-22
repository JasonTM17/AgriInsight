package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.integration.infrastructure.PostgresOutboxWriter;
import com.agriinsight.backend.shared.application.CommandCommittedEvent;
import com.agriinsight.backend.shared.application.CommandTarget;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class OutboxAtomicityIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString(
            "41000000-0000-0000-0000-000000000005");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, commandSql());
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runtimeInsertCommitsWithTransactionAndRollsBackWithMutation() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new TestPrincipal(), null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresOutboxWriter writer = new PostgresOutboxWriter(
                    harness.jdbcTemplate(), new ObjectMapper());
            harness.withinTenant(() -> {
                writer.append(event(
                        UUID.fromString("77000000-0000-0000-0000-000000000012"),
                        UUID.fromString("77000000-0000-0000-0000-000000000030")));
                return null;
            });
            try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
                assertThat(count(operator, "SELECT count(*) FROM outbox_events")).isEqualTo(1);
                assertThat(scalar(operator, "SELECT payload->>'event_id' FROM outbox_events"))
                        .isEqualTo(scalar(operator, "SELECT id::text FROM outbox_events"));
                assertThat(scalar(operator, "SELECT payload->'payload'->>'resource_type' FROM outbox_events"))
                        .isEqualTo("FARM");
            }

            assertThatThrownBy(() -> harness.withinTenant(() -> {
                writer.append(event(
                        UUID.fromString("77000000-0000-0000-0000-000000000013"),
                        UUID.fromString("77000000-0000-0000-0000-000000000031")));
                throw new IllegalStateException("rollback fixture");
            })).isInstanceOf(IllegalStateException.class);
            try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
                assertThat(count(operator, "SELECT count(*) FROM outbox_events")).isEqualTo(1);
            }
        }
    }

    private static CommandCommittedEvent event(UUID commandId, UUID aggregateId) {
        return new CommandCommittedEvent(
                TENANT_ID,
                PROFILE_ID,
                commandId,
                "/api/v1/farms",
                new CommandTarget("FARM", aggregateId, 0),
                Optional.of("outbox-test"),
                Instant.parse("2027-09-01T00:00:00Z"),
                0);
    }

    private static String commandSql() {
        return """
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                VALUES
                    ('77000000-0000-0000-0000-000000000012',
                     '10000000-0000-0000-0000-000000000041',
                     '41000000-0000-0000-0000-000000000005', 'POST',
                     '/api/v1/farms', repeat('3', 64), 1, repeat('c', 64), 'IN_PROGRESS'),
                    ('77000000-0000-0000-0000-000000000013',
                     '10000000-0000-0000-0000-000000000041',
                     '41000000-0000-0000-0000-000000000005', 'POST',
                     '/api/v1/farms', repeat('4', 64), 1, repeat('d', 64), 'IN_PROGRESS');
                """;
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
