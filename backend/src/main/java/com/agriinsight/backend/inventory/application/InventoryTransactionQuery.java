package com.agriinsight.backend.inventory.application;

import com.agriinsight.backend.inventory.domain.InventoryTransactionKind;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InventoryTransactionQuery(
        int limit,
        int offset,
        Optional<UUID> warehouseId,
        Optional<UUID> materialId,
        Optional<InventoryTransactionKind> kind,
        Optional<Instant> occurredFrom,
        Optional<Instant> occurredTo) {

    public InventoryTransactionQuery {
        requirePage(limit, offset);
        warehouseId = Objects.requireNonNull(warehouseId, "warehouseId is required");
        materialId = Objects.requireNonNull(materialId, "materialId is required");
        kind = Objects.requireNonNull(kind, "kind is required");
        occurredFrom = Objects.requireNonNull(occurredFrom, "occurredFrom is required");
        occurredTo = Objects.requireNonNull(occurredTo, "occurredTo is required");
        if (occurredFrom.isPresent() && occurredTo.isPresent()
                && occurredFrom.orElseThrow().isAfter(occurredTo.orElseThrow())) {
            throw new IllegalArgumentException("occurredFrom cannot be after occurredTo");
        }
    }

    static void requirePage(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (offset < 0 || offset > 10_000) {
            throw new IllegalArgumentException("offset must be between 0 and 10000");
        }
    }
}
