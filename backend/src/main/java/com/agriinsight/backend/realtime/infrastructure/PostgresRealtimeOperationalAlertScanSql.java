package com.agriinsight.backend.realtime.infrastructure;

/** Parameterized SQL for fair, bounded operational-alert source scans. */
final class PostgresRealtimeOperationalAlertScanSql {

    static final String FIND_PUBLISH_BACKLOG_FROM_START = """
            WITH bounds AS (
                SELECT CAST(? AS timestamptz) AS threshold
            ), tenant_page AS (
                SELECT tenant.id
                  FROM tenants tenant
                 ORDER BY tenant.id
                 LIMIT ?
            )
            SELECT tenant.id AS tenant_id,
                   backlog.occurred_at AS source_occurred_at,
                   tenant.id AS cursor_tenant_id
              FROM tenant_page tenant
             CROSS JOIN bounds
              LEFT JOIN LATERAL (
                SELECT event.occurred_at
                  FROM outbox_events event
                 WHERE event.tenant_id = tenant.id
                   AND event.status IN ('PENDING', 'LEASED')
                   AND event.occurred_at <= bounds.threshold
                 ORDER BY event.occurred_at
                 LIMIT 1
              ) backlog ON TRUE
             ORDER BY tenant.id
            """;
    static final String FIND_PUBLISH_BACKLOG_AFTER_TENANT = """
            WITH bounds AS (
                SELECT CAST(? AS timestamptz) AS threshold
            ), tenant_page AS (
                SELECT tenant.id
                  FROM tenants tenant
                 WHERE tenant.id > ?
                 ORDER BY tenant.id
                 LIMIT ?
            )
            SELECT tenant.id AS tenant_id,
                   backlog.occurred_at AS source_occurred_at,
                   tenant.id AS cursor_tenant_id
              FROM tenant_page tenant
             CROSS JOIN bounds
              LEFT JOIN LATERAL (
                SELECT event.occurred_at
                  FROM outbox_events event
                 WHERE event.tenant_id = tenant.id
                   AND event.status IN ('PENDING', 'LEASED')
                   AND event.occurred_at <= bounds.threshold
                 ORDER BY event.occurred_at
                 LIMIT 1
              ) backlog ON TRUE
             ORDER BY tenant.id
            """;
    static final String FIND_DELIVERY_LAG_FROM_START = """
            WITH source_page AS MATERIALIZED (
                SELECT event.tenant_id, event.id, event.occurred_at, event.published_at
                  FROM outbox_events event
                 WHERE event.status = 'PUBLISHED'
                   AND event.published_at <= ?
                 ORDER BY event.published_at, event.id
                 LIMIT ?
            )
            SELECT source.tenant_id, source.id AS source_event_id,
                   source.occurred_at AS source_occurred_at,
                   source.published_at AS cursor_ordered_at, source.id AS cursor_ordered_id,
                   receipt.event_id AS receipt_event_id
              FROM source_page source
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = source.tenant_id
               AND receipt.event_id = source.id
             ORDER BY source.published_at, source.id
            """;
    static final String FIND_DELIVERY_LAG_AFTER_CURSOR = """
            WITH source_page AS MATERIALIZED (
                SELECT event.tenant_id, event.id, event.occurred_at, event.published_at
                  FROM outbox_events event
                 WHERE event.status = 'PUBLISHED'
                   AND event.published_at <= ?
                   AND (event.published_at, event.id) > (?, ?)
                 ORDER BY event.published_at, event.id
                 LIMIT ?
            )
            SELECT source.tenant_id, source.id AS source_event_id,
                   source.occurred_at AS source_occurred_at,
                   source.published_at AS cursor_ordered_at, source.id AS cursor_ordered_id,
                   receipt.event_id AS receipt_event_id
              FROM source_page source
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = source.tenant_id
               AND receipt.event_id = source.id
             ORDER BY source.published_at, source.id
            """;
    static final String FIND_UNRECOVERED_DLT_FROM_START = """
            WITH source_page AS MATERIALIZED (
                SELECT alert.id, alert.tenant_id, alert.source_event_id,
                       alert.source_occurred_at, alert.last_observed_at
                  FROM realtime_operational_alerts alert
                 WHERE alert.policy_code = 'REALTIME_DLT_RECORD'
                   AND alert.state = 'OPEN'
                   AND alert.source_event_id IS NOT NULL
                 ORDER BY alert.last_observed_at, alert.id
                 LIMIT ?
            )
            SELECT source.tenant_id, source.source_event_id,
                   source.source_occurred_at,
                   source.last_observed_at AS cursor_ordered_at, source.id AS cursor_ordered_id,
                   receipt.event_id AS receipt_event_id
              FROM source_page source
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = source.tenant_id
               AND receipt.event_id = source.source_event_id
             ORDER BY source.last_observed_at, source.id
            """;
    static final String FIND_UNRECOVERED_DLT_AFTER_CURSOR = """
            WITH source_page AS MATERIALIZED (
                SELECT alert.id, alert.tenant_id, alert.source_event_id,
                       alert.source_occurred_at, alert.last_observed_at
                  FROM realtime_operational_alerts alert
                 WHERE alert.policy_code = 'REALTIME_DLT_RECORD'
                   AND alert.state = 'OPEN'
                   AND alert.source_event_id IS NOT NULL
                   AND (alert.last_observed_at, alert.id) > (?, ?)
                 ORDER BY alert.last_observed_at, alert.id
                 LIMIT ?
            )
            SELECT source.tenant_id, source.source_event_id,
                   source.source_occurred_at,
                   source.last_observed_at AS cursor_ordered_at, source.id AS cursor_ordered_id,
                   receipt.event_id AS receipt_event_id
              FROM source_page source
              LEFT JOIN realtime_event_receipts receipt
                ON receipt.tenant_id = source.tenant_id
               AND receipt.event_id = source.source_event_id
             ORDER BY source.last_observed_at, source.id
            """;
    static final String FIND_SCAN_CURSOR = """
            SELECT cursor_tenant_id, cursor_ordered_at, cursor_ordered_id, cycle_started_at
              FROM realtime_operational_alert_scan_cursors
             WHERE policy_code = ?
            """;
    static final String UPSERT_SCAN_CURSOR = """
            INSERT INTO realtime_operational_alert_scan_cursors (
                policy_code, cursor_tenant_id, cursor_ordered_at, cursor_ordered_id,
                cycle_started_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (policy_code) DO UPDATE
               SET cursor_tenant_id = EXCLUDED.cursor_tenant_id,
                   cursor_ordered_at = EXCLUDED.cursor_ordered_at,
                   cursor_ordered_id = EXCLUDED.cursor_ordered_id,
                   cycle_started_at = EXCLUDED.cycle_started_at,
                   updated_at = GREATEST(
                       realtime_operational_alert_scan_cursors.updated_at,
                       EXCLUDED.updated_at)
            """;
    static final String CLEAR_SCAN_CURSOR = """
            DELETE FROM realtime_operational_alert_scan_cursors
             WHERE policy_code = ?
            """;

    private PostgresRealtimeOperationalAlertScanSql() {
    }
}
