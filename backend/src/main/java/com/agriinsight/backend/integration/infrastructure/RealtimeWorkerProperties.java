package com.agriinsight.backend.integration.infrastructure;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.modulith.NamedInterface;

@NamedInterface("realtime-worker")
@ConfigurationProperties("agriinsight.realtime")
public record RealtimeWorkerProperties(
        boolean publisherEnabled,
        boolean consumerEnabled,
        String workerId,
        int batchSize,
        Duration leaseDuration,
        Duration pollDelay,
        Duration sendTimeout,
        String topic,
        String deadLetterTopic,
        int partitions,
        short replicationFactor,
        int maxRecordBytes) {

    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]{1,249}");
    private static final Duration MAX_LEASE = Duration.ofMinutes(15);
    private static final Duration MAX_POLL_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_SEND_TIMEOUT = Duration.ofMinutes(1);

    public RealtimeWorkerProperties {
        if (!WORKER_ID.matcher(Objects.requireNonNull(workerId, "worker-id is required")).matches()) {
            throw new IllegalArgumentException("worker-id has an invalid format");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batch-size must be between 1 and 100");
        }
        requireDuration(leaseDuration, "lease-duration", Duration.ofSeconds(1), MAX_LEASE);
        requireDuration(pollDelay, "poll-delay", Duration.ofMillis(100), MAX_POLL_DELAY);
        requireDuration(sendTimeout, "send-timeout", Duration.ofSeconds(1), MAX_SEND_TIMEOUT);
        topic = requireTopic(topic, "topic");
        deadLetterTopic = requireTopic(deadLetterTopic, "dead-letter-topic");
        if (topic.equals(deadLetterTopic)) {
            throw new IllegalArgumentException("topic and dead-letter-topic must be distinct");
        }
        if (partitions < 1 || partitions > 128) {
            throw new IllegalArgumentException("partitions must be between 1 and 128");
        }
        if (replicationFactor < 1 || replicationFactor > 7) {
            throw new IllegalArgumentException("replication-factor must be between 1 and 7");
        }
        if (maxRecordBytes < 1024 || maxRecordBytes > 1_048_576) {
            throw new IllegalArgumentException("max-record-bytes must be between 1024 and 1048576");
        }
    }

    private static String requireTopic(String value, String field) {
        String required = Objects.requireNonNull(value, field + " is required");
        if (!TOPIC.matcher(required).matches() || required.equals(".") || required.equals("..")) {
            throw new IllegalArgumentException(field + " has an invalid Kafka topic name");
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
