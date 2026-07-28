package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RealtimeSummaryResponse(
        String lens,
        String source,
        UUID tenantId,
        long eventCount,
        Instant lastOccurredAt,
        Instant lastProcessedAt,
        long freshnessSeconds,
        List<RealtimeMetricResponse> items,
        int limit,
        boolean hasMore) {

    static RealtimeSummaryResponse from(RealtimeSummary summary) {
        return new RealtimeSummaryResponse(
                RealtimeSummary.LENS, RealtimeSummary.SOURCE, summary.tenantId(),
                summary.eventCount(), summary.lastOccurredAt().orElse(null),
                summary.lastProcessedAt().orElse(null), summary.freshnessSeconds(),
                summary.items().stream().map(RealtimeMetricResponse::from).toList(),
                summary.limit(), summary.hasMore());
    }
}
