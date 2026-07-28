package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.TENANT_A;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.realtimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.realtime.application.RealtimeEventConflictException;
import com.agriinsight.backend.realtime.application.RealtimeEventIngestionService;
import com.agriinsight.backend.realtime.application.RealtimeEventOrderingException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore.ApplyResult;
import com.agriinsight.backend.realtime.infrastructure.PostgresRealtimeReadModelStore;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
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
class RealtimeReadModelStoreIntegrationTest {

    private static final UUID AGGREGATE_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void projectsOnlyCommittedOrderedMetadataAndRollsBackRejectedRecords() throws Exception {
        try (Connection realtime = realtimeConnection(POSTGRESQL, "agriinsight")) {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(realtime, true);
            RealtimeEventIngestionService service = service(dataSource);
            RealtimeOperationalEvent baseline = event(EVENT_ID, 3, "a".repeat(64), 10);

            assertThat(service.ingest(baseline)).isEqualTo(ApplyResult.APPLIED);
            assertThat(service.ingest(baseline)).isEqualTo(ApplyResult.DUPLICATE);
            assertThat(service.ingest(event(
                    UUID.fromString("70000000-0000-0000-0000-000000000002"),
                    4,
                    "b".repeat(64),
                    11))).isEqualTo(ApplyResult.APPLIED);

            assertThatThrownBy(() -> service.ingest(event(
                    UUID.fromString("70000000-0000-0000-0000-000000000003"),
                    4,
                    "c".repeat(64),
                    12)))
                    .isInstanceOf(RealtimeEventOrderingException.class)
                    .extracting(RealtimeEventOrderingException.class::cast)
                    .extracting(RealtimeEventOrderingException::reason)
                    .isEqualTo(RealtimeEventOrderingException.Reason.STALE);
            assertThatThrownBy(() -> service.ingest(event(
                    UUID.fromString("70000000-0000-0000-0000-000000000004"),
                    6,
                    "d".repeat(64),
                    13)))
                    .isInstanceOf(RealtimeEventOrderingException.class)
                    .extracting(RealtimeEventOrderingException.class::cast)
                    .extracting(RealtimeEventOrderingException::reason)
                    .isEqualTo(RealtimeEventOrderingException.Reason.GAP);
            assertThatThrownBy(() -> service.ingest(event(EVENT_ID, 3, "e".repeat(64), 10)))
                    .isInstanceOf(RealtimeEventConflictException.class);

            try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
                assertThat(count(operator, "SELECT count(*) FROM realtime_event_receipts"))
                        .isEqualTo(2);
                assertThat(count(operator, "SELECT count(*) FROM realtime_tenant_metrics"))
                        .isEqualTo(1);
                assertThat(scalar(operator, """
                        SELECT event_count::text
                          FROM realtime_tenant_metrics
                         WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                           AND event_type = 'AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED'
                        """)).isEqualTo("2");
                assertThat(scalar(operator, """
                        SELECT last_version::text
                          FROM realtime_aggregate_progress
                         WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                           AND aggregate_type = 'FARM'
                           AND aggregate_id = '71000000-0000-0000-0000-000000000001'
                        """)).isEqualTo("4");
            }
        }
    }

    private static RealtimeEventIngestionService service(SingleConnectionDataSource dataSource) {
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        transaction.setName("realtime-read-model-test");
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new RealtimeEventIngestionService(
                new PostgresRealtimeReadModelStore(new JdbcTemplate(dataSource)), transaction);
    }

    private static RealtimeOperationalEvent event(
            UUID eventId,
            long aggregateVersion,
            String checksum,
            long offset) {
        return new RealtimeOperationalEvent(
                eventId,
                TENANT_A,
                "FARM",
                AGGREGATE_ID,
                aggregateVersion,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T00:00:00Z"),
                checksum,
                "agriinsight.operational.v1",
                0,
                offset);
    }
}
