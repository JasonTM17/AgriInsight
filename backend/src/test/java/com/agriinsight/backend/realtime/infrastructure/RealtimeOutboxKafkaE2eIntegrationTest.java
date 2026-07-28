package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.persistence.support.PostgresIntegrationSupport;
import com.agriinsight.backend.realtime.api.RealtimeSummaryResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {
        "agriinsight.identity.enabled=true",
        "agriinsight.identity.issuer-uri=https://identity.example.test/issuer",
        "agriinsight.identity.jwk-set-uri=https://identity.example.test/jwks",
        "agriinsight.identity.api-audience=agriinsight-api",
        "agriinsight.identity.interactive-client-id=interactive-client",
        "agriinsight.identity.clock-skew=30s",
        "agriinsight.identity.jws-algorithm=RS256",
        "agriinsight.identity.discriminator-location=CLAIM",
        "agriinsight.identity.discriminator-name=token_use",
        "agriinsight.identity.discriminator-value=access",
        "agriinsight.identity.display-name-claim=name",
        "agriinsight.identity.email-claim=email",
        "agriinsight.identity.assurance-claim=acr",
        "agriinsight.identity.cors-allowed-origins[0]=https://app.agriinsight.test",
        "agriinsight.realtime.publisher-enabled=false",
        "agriinsight.realtime.consumer-enabled=false",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("realtime-e2e")
class RealtimeOutboxKafkaE2eIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @Container
    private static final KafkaContainer KAFKA = RealtimeKafkaE2eSupport.kafka();

    private static PostgresRealtimeE2eFixture database;
    private static RealtimeWorkerE2eHarness worker;

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private TenantPrincipalLoader principalLoader;

    private AuthenticatedRealtimeSummaryApi summaryApi;

    @DynamicPropertySource
    static void configureRealApplication(DynamicPropertyRegistry registry) {
        try {
            PostgresRealtimeE2eFixture.prepare(POSTGRESQL);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare the realtime E2E database", exception);
        }
        registry.add("spring.datasource.url", () ->
                PostgresIntegrationSupport.jdbcUrl(POSTGRESQL, "agriinsight"));
        registry.add("spring.datasource.username", () -> PostgresIntegrationSupport.RUNTIME);
        registry.add("spring.datasource.password", () -> PostgresIntegrationSupport.RUNTIME_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void prepareInfrastructure() throws Exception {
        database = PostgresRealtimeE2eFixture.create(POSTGRESQL);
        RealtimeKafkaE2eSupport.createTopics(KAFKA.getBootstrapServers());
        worker = RealtimeWorkerE2eHarness.start(POSTGRESQL, KAFKA.getBootstrapServers());
    }

    @AfterAll
    static void closeInfrastructure() {
        try {
            if (worker != null) {
                worker.close();
            }
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    @BeforeEach
    void authenticateSummaryApi() {
        summaryApi = new AuthenticatedRealtimeSummaryApi(mockMvc, jsonMapper);
        RealtimeE2eIdentityFixture.configure(jwtDecoder, principalLoader);
    }

    @Test
    @Timeout(300)
    void deliversAnOutboxEventThroughKafkaAndPreservesRecoveryDedupeRlsAndDltInvariants()
            throws Throwable {
        String bootstrapServers = KAFKA.getBootstrapServers();
        OutboxEvent first = database.append(PostgresRealtimeE2eFixture.FIRST_COMMAND_ID, 0);

        RealtimeKafkaE2eFaultAssertions.assertPausedKafkaBrokerRequeues(
                database, KAFKA, first, bootstrapServers, worker::publishAvailable);

        Instant recoveryStarted = Instant.now();
        RealtimeKafkaE2eSupport.await("requeued outbox recovery", Duration.ofSeconds(20), () -> {
            worker.publishAvailable();
            return database.outbox(first.commandId()).publishedAt().isPresent();
        });
        RealtimeSummaryResponse firstSummary = awaitSummary(1);
        assertThat(Duration.between(recoveryStarted, Instant.now()))
                .isLessThanOrEqualTo(RealtimeFreshnessE2eAssertions.P95_TARGET);
        assertThat(firstSummary.freshnessSeconds())
                .isBetween(0L, RealtimeFreshnessE2eAssertions.P95_TARGET.toSeconds());
        assertThat(firstSummary.items()).singleElement().satisfies(metric ->
                assertThat(metric.eventCount()).isEqualTo(1));
        RealtimeKafkaE2eSupport.await("initial consumer commit", Duration.ofSeconds(20),
                () -> RealtimeKafkaE2eSupport.committedOffset(
                        bootstrapServers, RealtimeKafkaE2eSupport.TOPIC) > 0);
        long committedBeforeRestart = RealtimeKafkaE2eSupport.committedOffset(
                bootstrapServers, RealtimeKafkaE2eSupport.TOPIC);

        worker.stopConsumers();
        RealtimeKafkaE2eFaultAssertions.publishDuplicateRecord(bootstrapServers, first);
        OutboxEvent second = database.append(PostgresRealtimeE2eFixture.SECOND_COMMAND_ID, 1);
        RealtimeKafkaE2eSupport.await("ordered outbox publication", Duration.ofSeconds(20), () -> {
            worker.publishAvailable();
            return database.outbox(second.commandId()).publishedAt().isPresent();
        });
        worker.startConsumers();
        RealtimeKafkaE2eSupport.await("restart consumption without loss", Duration.ofSeconds(20),
                () -> RealtimeKafkaE2eSupport.committedOffset(
                        bootstrapServers, RealtimeKafkaE2eSupport.TOPIC) >= committedBeforeRestart + 2);
        RealtimeSummaryResponse orderedSummary = awaitSummary(2);
        assertThat(database.aggregateVersion()).isEqualTo(1);
        assertThat(orderedSummary.items()).singleElement().satisfies(metric ->
                assertThat(metric.eventCount()).isEqualTo(2));

        RealtimeKafkaE2eFaultAssertions.assertPoisonRecordReachesDlt(bootstrapServers, first);
        assertThat(awaitSummary(2).eventCount()).isEqualTo(2);
        assertTenantBReceivesNoTenantAMetrics();
        Duration freshnessP95 = RealtimeFreshnessE2eAssertions.assertAuthorizedSummaryP95(
                database, worker, 2, this::awaitSummary);
        System.out.printf(
                "REALTIME_E2E result=PASS freshness_seconds=%d recovery_millis=%d freshness_p95_millis=%d samples=%d%n",
                orderedSummary.freshnessSeconds(),
                Duration.between(recoveryStarted, Instant.now()).toMillis(),
                freshnessP95.toMillis(),
                RealtimeFreshnessE2eAssertions.SAMPLE_COUNT);
    }

    private RealtimeSummaryResponse awaitSummary(long expectedEventCount) throws Throwable {
        final RealtimeSummaryResponse[] observed = new RealtimeSummaryResponse[1];
        RealtimeKafkaE2eSupport.await("authenticated tenant summary count " + expectedEventCount,
                Duration.ofSeconds(20), () -> {
                    observed[0] = summaryApi.summary(RealtimeE2eIdentityFixture.TENANT_A_TOKEN);
                    return observed[0].eventCount() == expectedEventCount;
                });
        return observed[0];
    }

    private void assertTenantBReceivesNoTenantAMetrics() throws Exception {
        RealtimeSummaryResponse denied = summaryApi.summary(RealtimeE2eIdentityFixture.TENANT_B_TOKEN);
        assertThat(denied.tenantId()).isEqualTo(PostgresRealtimeE2eFixture.TENANT_B);
        assertThat(denied.eventCount()).isZero();
        assertThat(denied.items()).isEmpty();
    }

}
