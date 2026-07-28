package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.realtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore.ApplyResult;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeReadModelStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RealtimeReadModelConcurrencyIntegrationTest {

    private static final String EVENT_TYPE = "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void keepsProcessingFreshnessMonotonicWhenAnOlderTransactionCommitsLast() throws Exception {
        try (Connection earlier = realtimeConnection(POSTGRESQL, "agriinsight");
                Connection newer = realtimeConnection(POSTGRESQL, "agriinsight");
                Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            SingleConnectionDataSource earlierDataSource = new SingleConnectionDataSource(earlier, true);
            SingleConnectionDataSource newerDataSource = new SingleConnectionDataSource(newer, true);
            RealtimeEventIngestionService newerService = service(newerDataSource);
            assertThat(newerService.ingest(event(
                    "70000000-0000-0000-0000-000000000110",
                    "71000000-0000-0000-0000-000000000110",
                    10)))
                    .isEqualTo(ApplyResult.APPLIED);

            CountDownLatch earlierTransactionStarted = new CountDownLatch(1);
            CountDownLatch newerProjectionCommitted = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Void> earlierProjection = executor.submit(() -> {
                    TransactionTemplate transaction = transaction(earlierDataSource);
                    JdbcTemplate earlierJdbc = new JdbcTemplate(earlierDataSource);
                    PostgresRealtimeReadModelStore earlierStore = new PostgresRealtimeReadModelStore(earlierJdbc);
                    transaction.execute(status -> {
                        earlierJdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
                        earlierTransactionStarted.countDown();
                        await(newerProjectionCommitted);
                        earlierStore.apply(event(
                                "70000000-0000-0000-0000-000000000112",
                                "71000000-0000-0000-0000-000000000112",
                                12));
                        return null;
                    });
                    return null;
                });

                assertThat(earlierTransactionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(25);
                assertThat(newerService.ingest(event(
                        "70000000-0000-0000-0000-000000000111",
                        "71000000-0000-0000-0000-000000000111",
                        11)))
                        .isEqualTo(ApplyResult.APPLIED);
                Instant newerProcessedAt = metricLastProcessedAt(operator);
                newerProjectionCommitted.countDown();
                earlierProjection.get(5, TimeUnit.SECONDS);

                assertThat(metricLastProcessedAt(operator)).isAfterOrEqualTo(newerProcessedAt);
            } finally {
                newerProjectionCommitted.countDown();
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    private static RealtimeEventIngestionService service(SingleConnectionDataSource dataSource) {
        return new RealtimeEventIngestionService(
                new PostgresRealtimeReadModelStore(new JdbcTemplate(dataSource)), transaction(dataSource));
    }

    private static TransactionTemplate transaction(SingleConnectionDataSource dataSource) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private static RealtimeOperationalEvent event(String eventId, String aggregateId, long offset) {
        return new RealtimeOperationalEvent(
                UUID.fromString(eventId),
                TENANT_A,
                "FARM",
                UUID.fromString(aggregateId),
                0,
                EVENT_TYPE,
                Instant.parse("2027-09-01T00:00:00Z"),
                Long.toHexString(offset).repeat(64).substring(0, 64),
                "agriinsight.operational.v1",
                0,
                offset);
    }

    private static Instant metricLastProcessedAt(Connection operator) throws SQLException {
        try (var statement = operator.prepareStatement("""
                SELECT last_processed_at
                  FROM realtime_tenant_metrics
                 WHERE tenant_id = ?
                   AND event_type = ?
                """)) {
            statement.setObject(1, TENANT_A);
            statement.setString(2, EVENT_TYPE);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getTimestamp("last_processed_at").toInstant();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for the newer projection");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the newer projection", exception);
        }
    }
}
