package com.agriinsight.backend.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class RealtimeEventIngestionServiceTest {

    @Test
    void appliesTheReadModelInsideTheConfiguredTransaction() {
        RealtimeReadModelStore store = mock(RealtimeReadModelStore.class);
        when(store.apply(event())).thenReturn(RealtimeReadModelStore.ApplyResult.APPLIED);

        RealtimeReadModelStore.ApplyResult result = new RealtimeEventIngestionService(
                store, immediateTransaction()).ingest(event());

        assertThat(result).isEqualTo(RealtimeReadModelStore.ApplyResult.APPLIED);
        verify(store).apply(event());
    }

    @Test
    void propagatesStoreFailuresSoTheTransactionCanRollBack() {
        RealtimeReadModelStore store = mock(RealtimeReadModelStore.class);
        when(store.apply(event())).thenThrow(new RealtimeEventOrderingException(
                RealtimeEventOrderingException.Reason.GAP));

        assertThatThrownBy(() -> new RealtimeEventIngestionService(
                store, immediateTransaction()).ingest(event()))
                .isInstanceOf(RealtimeEventOrderingException.class)
                .extracting(RealtimeEventOrderingException.class::cast)
                .extracting(RealtimeEventOrderingException::reason)
                .isEqualTo(RealtimeEventOrderingException.Reason.GAP);
    }

    private static TransactionOperations immediateTransaction() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> callback) {
                return callback.doInTransaction(null);
            }
        };
    }

    private static RealtimeOperationalEvent event() {
        return new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                0,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T00:00:00Z"),
                "a".repeat(64),
                "agriinsight.operational.v1",
                0,
                1);
    }
}
