package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeMetric;
import java.time.Instant;

public record RealtimeMetricResponse(
        String eventType,
        String aggregateType,
        long eventCount,
        Instant lastOccurredAt,
        Instant lastProcessedAt) {

    static RealtimeMetricResponse from(RealtimeMetric metric) {
        return new RealtimeMetricResponse(
                metric.eventType(), metric.aggregateType(), metric.eventCount(),
                metric.lastOccurredAt(), metric.lastProcessedAt());
    }
}
