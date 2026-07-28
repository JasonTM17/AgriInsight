package com.agriinsight.backend.realtime.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;

/** Creates the intentionally fixed, payload-free terminal observer failure record. */
final class RealtimeTerminalObserverFailureRecordFactory {

    private static final byte[] TERMINAL_FAILURE_MARKER =
            "terminal-observer-failure".getBytes(StandardCharsets.US_ASCII);

    private final String failureTopic;

    RealtimeTerminalObserverFailureRecordFactory(String failureTopic) {
        this.failureTopic = Objects.requireNonNull(failureTopic, "failureTopic is required");
    }

    ProducerRecord<Object, Object> create() {
        return new ProducerRecord<>(
                failureTopic,
                null,
                null,
                null,
                TERMINAL_FAILURE_MARKER.clone(),
                new RecordHeaders());
    }
}
