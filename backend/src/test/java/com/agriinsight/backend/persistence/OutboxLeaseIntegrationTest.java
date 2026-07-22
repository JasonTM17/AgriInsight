package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.integration.application.OutboxDrainService;
import com.agriinsight.backend.integration.infrastructure.PostgresOutboxStore;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class OutboxLeaseIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, seedSql());
        }
    }

    @Test
    void leasesInOrderFencesStaleAcknowledgementsAndDeadLettersBoundedRetries() throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "SET ROLE agriinsight_integration");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(
                    new SingleConnectionDataSource(operator, false));
            PostgresOutboxStore store = new PostgresOutboxStore(jdbcTemplate);
            OutboxDrainService drain = new OutboxDrainService(store);
            Instant now = Instant.now();

            var first = drain.lease("worker-a", 1, Duration.ofSeconds(30), now);
            assertThat(first).singleElement().satisfies(lease -> {
                assertThat(lease.event().aggregateVersion()).isZero();
                assertThat(lease.event().status()).isEqualTo(
                        com.agriinsight.backend.integration.domain.OutboxStatus.LEASED);
            });
            var stale = new OutboxDrainService.OutboxLease(
                    first.get(0).event(), "worker-b", UUID.randomUUID(), first.get(0).generation());
            assertThat(drain.acknowledge(stale, now.plusSeconds(1))).isFalse();
            assertThat(drain.acknowledge(first.get(0), now.plusSeconds(1))).isTrue();

            var second = drain.lease("worker-b", 1, Duration.ofSeconds(30), now.plusSeconds(2));
            assertThat(second).singleElement().satisfies(lease -> {
                assertThat(lease.event().aggregateVersion()).isEqualTo(1);
                assertThat(drain.acknowledge(lease, now.plusSeconds(3))).isTrue();
            });

            Instant cursor = now.plusSeconds(4);
            for (int attempt = 1; attempt <= 5; attempt++) {
                int attemptNumber = attempt;
                Instant attemptNow = cursor;
                var failed = drain.lease("worker-c", 1, Duration.ofSeconds(30), attemptNow);
                assertThat(failed).singleElement().satisfies(lease -> {
                    assertThat(lease.event().aggregateVersion()).isZero();
                    assertThat(drain.fail(lease, "delivery failed", attemptNow.plusSeconds(1)))
                            .isEqualTo(attemptNumber == 5
                                    ? OutboxDrainService.FailureResult.DEAD_LETTER
                                    : OutboxDrainService.FailureResult.REQUEUED);
                });
                cursor = cursor.plusSeconds(10);
            }
            assertThat(drain.lease("worker-c", 1, Duration.ofSeconds(30), cursor)).isEmpty();
        }
    }

    private static String seedSql() {
        return """
                INSERT INTO api_command_records (
                    id, tenant_id, principal_id, http_method, route_template,
                    idempotency_key_digest, canonical_schema_version, command_hash, state)
                VALUES
                    ('77000000-0000-0000-0000-000000000010',
                     '10000000-0000-0000-0000-000000000041',
                     '41000000-0000-0000-0000-000000000005', 'POST',
                     '/api/v1/cost-entries', repeat('1', 64), 1, repeat('a', 64), 'IN_PROGRESS'),
                    ('77000000-0000-0000-0000-000000000011',
                     '10000000-0000-0000-0000-000000000041',
                     '41000000-0000-0000-0000-000000000005', 'POST',
                     '/api/v1/cost-entries', repeat('2', 64), 1, repeat('b', 64), 'IN_PROGRESS'),
                    ('77000000-0000-0000-0000-000000000012',
                     '10000000-0000-0000-0000-000000000041',
                     '41000000-0000-0000-0000-000000000005', 'POST',
                     '/api/v1/cost-entries', repeat('3', 64), 1, repeat('c', 64), 'IN_PROGRESS');
                INSERT INTO outbox_events (
                    id, tenant_id, command_id, event_ordinal, aggregate_type,
                    aggregate_id, aggregate_version, event_type, schema_version,
                    occurred_at, payload)
                VALUES
                    ('77000000-0000-0000-0000-000000000020',
                     '10000000-0000-0000-0000-000000000041',
                     '77000000-0000-0000-0000-000000000010', 0, 'FARM',
                     '77000000-0000-0000-0000-000000000001', 0,
                     'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                     CURRENT_TIMESTAMP, '{}'::jsonb),
                    ('77000000-0000-0000-0000-000000000021',
                     '10000000-0000-0000-0000-000000000041',
                     '77000000-0000-0000-0000-000000000011', 0, 'FARM',
                     '77000000-0000-0000-0000-000000000001', 1,
                     'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                     CURRENT_TIMESTAMP, '{}'::jsonb),
                    ('77000000-0000-0000-0000-000000000022',
                     '10000000-0000-0000-0000-000000000041',
                     '77000000-0000-0000-0000-000000000012', 0, 'FARM',
                     '77000000-0000-0000-0000-000000000002', 0,
                     'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED', 1,
                     CURRENT_TIMESTAMP, '{}'::jsonb);
                """;
    }
}
