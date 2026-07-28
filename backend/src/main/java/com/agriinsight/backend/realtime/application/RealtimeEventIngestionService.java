package com.agriinsight.backend.realtime.application;

import java.util.Objects;
import org.springframework.transaction.support.TransactionOperations;

/** Applies one validated Kafka event in the transaction that makes its read model durable. */
public class RealtimeEventIngestionService {

    private final RealtimeReadModelStore store;
    private final TransactionOperations transaction;

    public RealtimeEventIngestionService(
            RealtimeReadModelStore store,
            TransactionOperations transaction) {
        this.store = Objects.requireNonNull(store, "store is required");
        this.transaction = Objects.requireNonNull(transaction, "transaction is required");
    }

    public RealtimeReadModelStore.ApplyResult ingest(RealtimeOperationalEvent event) {
        RealtimeOperationalEvent required = Objects.requireNonNull(event, "event is required");
        return transaction.execute(status -> Objects.requireNonNull(
                store.apply(required), "read model store result is required"));
    }
}
