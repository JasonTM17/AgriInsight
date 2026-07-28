package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.realtime.application.RealtimeEventConflictException;
import com.agriinsight.backend.realtime.application.RealtimeEventOrderingException;
import com.agriinsight.backend.realtime.application.RealtimeOperationalEvent;
import com.agriinsight.backend.realtime.application.RealtimeReadModelStore.ApplyResult;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostgresRealtimeReadModelStoreTest {

    @BeforeEach
    void activateTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void failsClosedOutsideAnActiveTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> new PostgresRealtimeReadModelStore(mock(JdbcTemplate.class)).apply(event(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("realtime read model requires an active transaction");
    }

    @Test
    void appliesTheFirstObservedAggregateVersionWithoutPersistingThePayload() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThat(new PostgresRealtimeReadModelStore(jdbcTemplate).apply(event(0)))
                .isEqualTo(ApplyResult.APPLIED);

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).update(statements.capture(), any(Object[].class));
        assertThat(statements.getAllValues())
                .anySatisfy(statement -> assertThat(statement).contains("realtime_event_receipts"))
                .anySatisfy(statement -> assertThat(statement).contains("realtime_aggregate_progress"))
                .anySatisfy(statement -> assertThat(statement).contains("realtime_tenant_metrics"))
                .allSatisfy(statement -> assertThat(statement).doesNotContainIgnoringCase("payload"));
    }

    @Test
    void deduplicatesAnIdenticalReceiptWithoutAdvancingProgressOrMetrics() throws Exception {
        RealtimeOperationalEvent event = event(0);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        receiptQueryReturns(jdbcTemplate, event.tenantId(), event.checksum());

        assertThat(new PostgresRealtimeReadModelStore(jdbcTemplate).apply(event))
                .isEqualTo(ApplyResult.DUPLICATE);

        verify(jdbcTemplate).update(contains("realtime_event_receipts"), any(Object[].class));
        verify(jdbcTemplate, never()).update(
                contains("realtime_aggregate_progress"), any(Object[].class));
        verify(jdbcTemplate, never()).update(
                contains("realtime_tenant_metrics"), any(Object[].class));
    }

    @Test
    void rejectsReusedEventIdsWithDifferentContent() throws Exception {
        RealtimeOperationalEvent event = event(0);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        receiptQueryReturns(jdbcTemplate, event.tenantId(), "b".repeat(64));

        assertThatThrownBy(() -> new PostgresRealtimeReadModelStore(jdbcTemplate).apply(event))
                .isInstanceOf(RealtimeEventConflictException.class);
        verify(jdbcTemplate, never()).update(
                contains("realtime_aggregate_progress"), any(Object[].class));
    }

    @Test
    void advancesAnExistingAggregateOnlyByItsNextVersion() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0, 1, 1);
        progressQueryReturns(jdbcTemplate, 0);

        assertThat(new PostgresRealtimeReadModelStore(jdbcTemplate).apply(event(1)))
                .isEqualTo(ApplyResult.APPLIED);

        verify(jdbcTemplate).update(contains("UPDATE realtime_aggregate_progress"), any(Object[].class));
    }

    @Test
    void rejectsStaleAndGappedAggregateVersionsBeforeWritingMetrics() throws Exception {
        JdbcTemplate staleJdbcTemplate = mock(JdbcTemplate.class);
        when(staleJdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        progressQueryReturns(staleJdbcTemplate, 1);

        assertThatThrownBy(() -> new PostgresRealtimeReadModelStore(staleJdbcTemplate).apply(event(1)))
                .isInstanceOf(RealtimeEventOrderingException.class)
                .extracting(RealtimeEventOrderingException.class::cast)
                .extracting(RealtimeEventOrderingException::reason)
                .isEqualTo(RealtimeEventOrderingException.Reason.STALE);
        verify(staleJdbcTemplate, never()).update(
                contains("realtime_tenant_metrics"), any(Object[].class));

        JdbcTemplate gappedJdbcTemplate = mock(JdbcTemplate.class);
        when(gappedJdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        progressQueryReturns(gappedJdbcTemplate, 0);

        assertThatThrownBy(() -> new PostgresRealtimeReadModelStore(gappedJdbcTemplate).apply(event(2)))
                .isInstanceOf(RealtimeEventOrderingException.class)
                .extracting(RealtimeEventOrderingException.class::cast)
                .extracting(RealtimeEventOrderingException::reason)
                .isEqualTo(RealtimeEventOrderingException.Reason.GAP);
        verify(gappedJdbcTemplate, never()).update(
                contains("realtime_tenant_metrics"), any(Object[].class));
    }

    @Test
    void rejectsAReusedKafkaBrokerCoordinate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("broker coordinate"));

        assertThatThrownBy(() -> new PostgresRealtimeReadModelStore(jdbcTemplate).apply(event(0)))
                .isInstanceOf(RealtimeEventConflictException.class);
        verify(jdbcTemplate, never()).update(
                contains("realtime_aggregate_progress"), any(Object[].class));
    }

    private static void receiptQueryReturns(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            String checksum) throws Exception {
        when(jdbcTemplate.query(
                contains("FROM realtime_event_receipts"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class)))
                .thenAnswer(invocation -> mappedReceipt(invocation, tenantId, checksum));
    }

    private static void progressQueryReturns(JdbcTemplate jdbcTemplate, long version) throws Exception {
        when(jdbcTemplate.query(
                contains("FROM realtime_aggregate_progress"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class)))
                .thenAnswer(invocation -> mappedProgress(invocation, version));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mappedReceipt(
            InvocationOnMock invocation,
            UUID tenantId,
            String checksum) throws Exception {
        RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("tenant_id", UUID.class)).thenReturn(tenantId);
        when(result.getString("checksum")).thenReturn(checksum);
        return List.of(mapper.mapRow(result, 0));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mappedProgress(InvocationOnMock invocation, long version) throws Exception {
        RowMapper<Object> mapper = (RowMapper<Object>) invocation.getArgument(1);
        ResultSet result = mock(ResultSet.class);
        when(result.getLong("last_version")).thenReturn(version);
        return List.of(mapper.mapRow(result, 0));
    }

    private static RealtimeOperationalEvent event(long aggregateVersion) {
        return new RealtimeOperationalEvent(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000041"),
                "FARM",
                UUID.fromString("71000000-0000-0000-0000-000000000001"),
                aggregateVersion,
                "AGRIINSIGHT.OPERATIONAL.FARM.COMMITTED",
                Instant.parse("2027-09-01T00:00:00Z"),
                "a".repeat(64),
                "agriinsight.operational.v1",
                0,
                1);
    }
}
