package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.integration.domain.OutboxEvent;
import com.agriinsight.backend.realtime.api.RealtimeSummaryResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Measures the accepted outbox-to-authorized-summary freshness budget without test-only shortcuts. */
final class RealtimeFreshnessE2eAssertions {

    static final Duration P95_TARGET = Duration.ofSeconds(30);
    static final int SAMPLE_COUNT = 20;

    private RealtimeFreshnessE2eAssertions() {
    }

    static Duration assertAuthorizedSummaryP95(
            PostgresRealtimeE2eFixture database,
            RealtimeWorkerE2eHarness worker,
            long initialEventCount,
            SummaryAwaiter summaryAwaiter) throws Throwable {
        List<Duration> samples = new ArrayList<>();
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            UUID commandId = deterministicId("freshness-command-" + index);
            UUID aggregateId = deterministicId("freshness-aggregate-" + index);
            Instant started = Instant.now();
            OutboxEvent sample = database.append(commandId, aggregateId, 0);
            RealtimeKafkaE2eSupport.await("freshness sample outbox publication", P95_TARGET, () -> {
                worker.publishAvailable();
                return database.outbox(sample.commandId()).publishedAt().isPresent();
            });
            RealtimeSummaryResponse summary = summaryAwaiter.await(initialEventCount + index + 1);
            samples.add(Duration.between(started, Instant.now()));
            assertThat(summary.freshnessSeconds()).isBetween(0L, P95_TARGET.toSeconds());
        }

        Duration p95 = samples.stream().sorted(Comparator.naturalOrder())
                .toList().get((int) Math.ceil(samples.size() * 0.95d) - 1);
        assertThat(p95).isLessThanOrEqualTo(P95_TARGET);
        return p95;
    }

    private static UUID deterministicId(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    interface SummaryAwaiter {
        RealtimeSummaryResponse await(long expectedEventCount) throws Throwable;
    }
}
