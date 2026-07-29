package com.agriinsight.backend.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.realtime.application.RealtimeOperationalAlertView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PostgresRealtimeOperationalAlertQueryStoreTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();

    @Test
    void latestFeedUsesOneFixedLookaheadQueryAndOnlyValidSafeEvidenceRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers
                                .<RowMapper<RealtimeOperationalAlertView>>any(),
                        any(Object[].class)))
                .thenReturn(List.of());
        PostgresRealtimeOperationalAlertQueryStore store =
                new PostgresRealtimeOperationalAlertQueryStore(jdbcTemplate);

        assertThat(store.findLatestOpen(
                        TENANT_ID, PROFILE_ID, Instant.parse("2027-09-01T03:00:00Z")))
                .isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<RealtimeOperationalAlertView>>any(),
                eq(PROFILE_ID),
                eq(TENANT_ID));
        assertThat(sql.getValue())
                .contains("WHERE alert.tenant_id = ?")
                .contains("alert.state = 'OPEN'")
                .contains("alert.source_occurred_at IS NOT NULL")
                .contains("alert.policy_code = 'OUTBOX_PUBLISH_BACKLOG'")
                .contains("alert.source_event_id IS NULL")
                .contains("alert.policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')")
                .contains("alert.source_event_id IS NOT NULL")
                .contains("WHEN 'CRITICAL' THEN 0")
                .contains("WHEN 'WARNING' THEN 1")
                .contains("alert.last_observed_at DESC")
                .contains("alert.id ASC")
                .contains("LIMIT 51")
                .doesNotContain("dedupe_key")
                .doesNotContain("payload")
                .doesNotContain("error");
    }

    @Test
    void currentAlertJoinIsBoundToTheCurrentProfileAndObservation() {
        assertThat(RealtimeOperationalAlertQuerySql.OPEN_BY_ID)
                .contains("acknowledgement.profile_id = ?")
                .contains("acknowledgement.acknowledged_observation_at = alert.last_observed_at")
                .contains("WHERE alert.tenant_id = ?")
                .contains("alert.id = ?");
    }
}
