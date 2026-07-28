package com.agriinsight.backend.realtime.infrastructure;

/** Parameterized SQL for bounded, current-condition verification during alert recovery. */
final class PostgresRealtimeOperationalAlertRecoverySql {

    static final String FIND_PUBLISH_BACKLOG_CANDIDATES = """
            WITH alert_page AS MATERIALIZED (
                SELECT alert.id, alert.tenant_id, alert.dedupe_key,
                       alert.clean_since, alert.clean_scan_count, alert.last_evaluated_at
                  FROM realtime_operational_alerts alert
                 WHERE alert.policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                   AND alert.state = 'OPEN'
                   AND alert.last_evaluated_at < ?
                 ORDER BY alert.last_evaluated_at, alert.id
                 LIMIT ?
            )
            SELECT alert.id AS alert_id, alert.dedupe_key, alert.clean_since,
                   alert.clean_scan_count,
                   condition.tenant_id AS condition_tenant_id,
                   NULL::uuid AS condition_source_event_id,
                   condition.source_occurred_at AS condition_source_occurred_at
              FROM alert_page alert
              LEFT JOIN LATERAL (
                SELECT event.tenant_id, event.occurred_at AS source_occurred_at
                  FROM outbox_events event
                 WHERE event.tenant_id = alert.tenant_id
                   AND event.status IN ('PENDING', 'LEASED')
                   AND event.occurred_at <= ?
                 ORDER BY event.occurred_at
                 LIMIT 1
              ) condition ON TRUE
             ORDER BY alert.last_evaluated_at, alert.id
            """;

    static final String FIND_DELIVERY_LAG_CANDIDATES = """
            WITH alert_page AS MATERIALIZED (
                SELECT alert.id, alert.tenant_id, alert.dedupe_key,
                       alert.source_event_id, alert.clean_since,
                       alert.clean_scan_count, alert.last_evaluated_at
                  FROM realtime_operational_alerts alert
                 WHERE alert.policy_code = 'REALTIME_DELIVERY_LAG'
                   AND alert.state = 'OPEN'
                   AND alert.last_evaluated_at < ?
                 ORDER BY alert.last_evaluated_at, alert.id
                 LIMIT ?
            )
            SELECT alert.id AS alert_id, alert.dedupe_key, alert.clean_since,
                   alert.clean_scan_count,
                   condition.tenant_id AS condition_tenant_id,
                   condition.source_event_id AS condition_source_event_id,
                   condition.source_occurred_at AS condition_source_occurred_at
              FROM alert_page alert
              LEFT JOIN LATERAL (
                SELECT event.tenant_id, event.id AS source_event_id,
                       event.occurred_at AS source_occurred_at
                  FROM outbox_events event
                  LEFT JOIN realtime_event_receipts receipt
                    ON receipt.tenant_id = event.tenant_id
                   AND receipt.event_id = event.id
                 WHERE event.id = alert.source_event_id
                   AND event.tenant_id = alert.tenant_id
                   AND event.status = 'PUBLISHED'
                   AND event.published_at <= ?
                   AND receipt.event_id IS NULL
              ) condition ON TRUE
             ORDER BY alert.last_evaluated_at, alert.id
            """;

    static final String FIND_DLT_CANDIDATES = """
            WITH alert_page AS MATERIALIZED (
                SELECT alert.id, alert.tenant_id, alert.dedupe_key,
                       alert.source_event_id, alert.source_occurred_at,
                       alert.clean_since, alert.clean_scan_count, alert.last_evaluated_at
                  FROM realtime_operational_alerts alert
                 WHERE alert.policy_code = 'REALTIME_DLT_RECORD'
                   AND alert.state = 'OPEN'
                   AND alert.last_evaluated_at < ?
                 ORDER BY alert.last_evaluated_at, alert.id
                 LIMIT ?
            )
            SELECT alert.id AS alert_id, alert.dedupe_key, alert.clean_since,
                   alert.clean_scan_count,
                   condition.tenant_id AS condition_tenant_id,
                   condition.source_event_id AS condition_source_event_id,
                   condition.source_occurred_at AS condition_source_occurred_at
              FROM alert_page alert
              LEFT JOIN LATERAL (
                SELECT alert.tenant_id, alert.source_event_id,
                       alert.source_occurred_at
                 WHERE alert.source_event_id IS NOT NULL
                   AND NOT EXISTS (
                       SELECT 1
                         FROM realtime_event_receipts receipt
                        WHERE receipt.tenant_id = alert.tenant_id
                          AND receipt.event_id = alert.source_event_id
                   )
              ) condition ON TRUE
             ORDER BY alert.last_evaluated_at, alert.id
            """;

    private PostgresRealtimeOperationalAlertRecoverySql() {
    }
}
