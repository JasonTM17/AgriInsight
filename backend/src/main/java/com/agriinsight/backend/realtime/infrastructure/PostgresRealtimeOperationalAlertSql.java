package com.agriinsight.backend.realtime.infrastructure;

/** Parameterized SQL used by the worker-owned operational alert projection. */
final class PostgresRealtimeOperationalAlertSql {

    static final String FIND_PUBLISH_BACKLOG = """
            SELECT tenant_id, MIN(occurred_at) AS source_occurred_at
              FROM outbox_events
             WHERE status IN ('PENDING', 'LEASED')
               AND occurred_at <= ?
             GROUP BY tenant_id
             ORDER BY MIN(occurred_at), tenant_id
             LIMIT ?
            """;
    static final String FIND_DELIVERY_LAG = """
            SELECT event.tenant_id, event.id AS source_event_id,
                   event.occurred_at AS source_occurred_at
              FROM outbox_events event
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = event.tenant_id
               AND receipt.event_id = event.id
             WHERE event.status = 'PUBLISHED'
               AND event.published_at <= ?
               AND receipt.event_id IS NULL
             ORDER BY event.published_at, event.id
            LIMIT ?
            """;
    static final String FIND_UNRECOVERED_DLT = """
            SELECT alert.tenant_id, alert.source_event_id,
                   alert.source_occurred_at
              FROM realtime_operational_alerts alert
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = alert.tenant_id
               AND receipt.event_id = alert.source_event_id
             WHERE alert.policy_code = 'REALTIME_DLT_RECORD'
               AND alert.state = 'OPEN'
               AND alert.source_event_id IS NOT NULL
               AND receipt.event_id IS NULL
             ORDER BY alert.last_observed_at, alert.id
             LIMIT ?
            """;
    static final String FIND_OPEN_ALERTS = """
            SELECT id, dedupe_key, clean_since, clean_scan_count
              FROM realtime_operational_alerts
             WHERE policy_code = ? AND state = 'OPEN'
             ORDER BY last_evaluated_at, id
             LIMIT ?
            """;
    static final String UPSERT_ALERT = """
            INSERT INTO realtime_operational_alerts (
                id, tenant_id, policy_code, dedupe_key, severity, state,
                source_event_id, source_occurred_at, opened_at, last_observed_at,
                resolved_at, clean_since, clean_scan_count, last_evaluated_at, version)
            VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, NULL, NULL, 0, ?, 0)
            ON CONFLICT (tenant_id, policy_code, dedupe_key) DO UPDATE
               SET severity = EXCLUDED.severity,
                   state = 'OPEN',
                   source_event_id = EXCLUDED.source_event_id,
                   source_occurred_at = EXCLUDED.source_occurred_at,
                   last_observed_at = CASE
                       WHEN realtime_operational_alerts.state = 'RESOLVED'
                         OR realtime_operational_alerts.source_event_id
                                IS DISTINCT FROM EXCLUDED.source_event_id
                         OR realtime_operational_alerts.source_occurred_at
                                IS DISTINCT FROM EXCLUDED.source_occurred_at
                       THEN EXCLUDED.last_observed_at
                       ELSE realtime_operational_alerts.last_observed_at
                   END,
                   resolved_at = NULL,
                   clean_since = NULL,
                   clean_scan_count = 0,
                   last_evaluated_at = GREATEST(
                       realtime_operational_alerts.last_evaluated_at,
                       EXCLUDED.last_evaluated_at),
                   version = realtime_operational_alerts.version + 1
            """;
    static final String RECORD_CLEAN = """
            UPDATE realtime_operational_alerts
               SET clean_since = ?,
                   clean_scan_count = ?,
                   state = CASE WHEN ? THEN 'RESOLVED' ELSE state END,
                   resolved_at = CASE WHEN ? THEN ? ELSE resolved_at END,
                   last_evaluated_at = GREATEST(last_evaluated_at, ?),
                   version = version + 1
             WHERE id = ? AND state = 'OPEN'
            """;

    private PostgresRealtimeOperationalAlertSql() {
    }
}
