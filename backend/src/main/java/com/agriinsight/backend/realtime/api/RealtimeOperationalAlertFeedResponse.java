package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertFeed;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RealtimeOperationalAlertFeedResponse(
        Instant generatedAt,
        List<RealtimeOperationalAlertResponse> items,
        int limit,
        boolean hasMore) {

    static RealtimeOperationalAlertFeedResponse from(
            RealtimeOperationalAlertFeed feed) {
        Objects.requireNonNull(feed, "feed is required");
        return new RealtimeOperationalAlertFeedResponse(
                feed.generatedAt(),
                feed.items().stream()
                        .map(RealtimeOperationalAlertResponse::from)
                        .toList(),
                feed.limit(),
                feed.hasMore());
    }
}
