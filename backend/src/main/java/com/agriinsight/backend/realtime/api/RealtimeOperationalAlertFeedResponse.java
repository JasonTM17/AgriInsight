package com.agriinsight.backend.realtime.api;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertFeed;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RealtimeOperationalAlertFeedResponse(
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        Instant generatedAt,
        @io.swagger.v3.oas.annotations.media.ArraySchema(
                arraySchema = @io.swagger.v3.oas.annotations.media.Schema(
                        requiredMode =
                                io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED),
                maxItems = RealtimeOperationalAlertFeed.LIMIT,
                schema = @io.swagger.v3.oas.annotations.media.Schema(
                        implementation = RealtimeOperationalAlertResponse.class))
        List<RealtimeOperationalAlertResponse> items,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                minimum = "50",
                maximum = "50")
        int limit,
        @io.swagger.v3.oas.annotations.media.Schema(
                requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
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
