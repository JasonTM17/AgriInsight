package com.agriinsight.backend.realtime.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One source page and its exclusive continuation cursor, if more source rows remain. */
public record RealtimeOperationalAlertScanPage(
        List<RealtimeOperationalAlertCandidate> candidates,
        Optional<RealtimeOperationalAlertScanCursor> continuationCursor,
        boolean hasMore) {

    public RealtimeOperationalAlertScanPage {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates are required"));
        continuationCursor = Objects.requireNonNull(
                continuationCursor, "continuationCursor is required");
        if (hasMore != continuationCursor.isPresent()) {
            throw new IllegalArgumentException("continuation cursor must match hasMore");
        }
    }
}
