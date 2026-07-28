package com.agriinsight.backend.realtime.infrastructure;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded configuration for the isolated operational-alert worker. */
@ConfigurationProperties("agriinsight.realtime.alerts")
public record RealtimeAlertWorkerProperties(
        boolean enabled,
        Duration evaluationDelay,
        Duration publishBacklogThreshold,
        Duration deliveryLagThreshold,
        Duration healthyFor,
        int requiredCleanScans,
        int maximumCandidates,
        String observerGroupId,
        String observerFailureTopic,
        int observerFailureRetries,
        Duration observerRetryInterval) {

    private static final Pattern KAFKA_NAME = Pattern.compile("[A-Za-z0-9._-]{1,249}");
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);
    private static final Duration MAX_THRESHOLD = Duration.ofDays(7);

    public RealtimeAlertWorkerProperties {
        requireDuration(evaluationDelay, "evaluation-delay", Duration.ofSeconds(1), MAX_DELAY);
        requireDuration(
                publishBacklogThreshold,
                "publish-backlog-threshold",
                Duration.ofSeconds(1),
                MAX_THRESHOLD);
        requireDuration(
                deliveryLagThreshold,
                "delivery-lag-threshold",
                Duration.ofSeconds(1),
                MAX_THRESHOLD);
        requireDuration(healthyFor, "healthy-for", Duration.ofSeconds(1), MAX_THRESHOLD);
        if (requiredCleanScans < 2 || requiredCleanScans > 100) {
            throw new IllegalArgumentException("required-clean-scans must be between 2 and 100");
        }
        if (maximumCandidates < 1 || maximumCandidates > 1_000) {
            throw new IllegalArgumentException("maximum-candidates must be between 1 and 1000");
        }
        observerGroupId = requireKafkaName(observerGroupId, "observer-group-id");
        observerFailureTopic = requireKafkaName(observerFailureTopic, "observer-failure-topic");
        if (observerFailureRetries < 0 || observerFailureRetries > 10) {
            throw new IllegalArgumentException("observer-failure-retries must be between 0 and 10");
        }
        requireDuration(
                observerRetryInterval,
                "observer-retry-interval",
                Duration.ofMillis(100),
                MAX_DELAY);
    }

    private static String requireKafkaName(String value, String field) {
        String required = Objects.requireNonNull(value, field + " is required");
        if (!KAFKA_NAME.matcher(required).matches() || required.equals(".") || required.equals("..")) {
            throw new IllegalArgumentException(field + " has an invalid Kafka name");
        }
        return required;
    }

    private static void requireDuration(
            Duration value, String field, Duration minimum, Duration maximum) {
        Duration required = Objects.requireNonNull(value, field + " is required");
        if (required.compareTo(minimum) < 0 || required.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum);
        }
    }
}
