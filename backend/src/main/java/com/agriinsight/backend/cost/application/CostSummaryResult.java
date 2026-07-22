package com.agriinsight.backend.cost.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CostSummaryResult(
        UUID tenantId,
        Instant occurredFrom,
        Instant occurredTo,
        CostSummaryGroup groupBy,
        List<CostSummaryItem> items,
        int limit,
        boolean hasMore) {

    public static final String LENS = "OPERATING_COST";
    public static final String SOURCE = "OPERATING_COST_LEDGER";

    public CostSummaryResult {
        Objects.requireNonNull(tenantId, "tenantId is required");
        CostPeriod.requireBounded(occurredFrom, occurredTo);
        Objects.requireNonNull(groupBy, "groupBy is required");
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        if (limit < 1 || items.size() > limit) {
            throw new IllegalArgumentException("Cost summary metadata is invalid");
        }
    }
}
