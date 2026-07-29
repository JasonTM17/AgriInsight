package com.agriinsight.backend.realtime.infrastructure;

/** Exact bounded SQL for the runtime operational-alert read boundary. */
final class RealtimeOperationalAlertQuerySql {

    private static final String SELECT_CURRENT_PROFILE = """
            SELECT alert.id, alert.policy_code, alert.severity, alert.state,
                   alert.source_event_id, alert.source_occurred_at,
                   alert.opened_at, alert.last_observed_at, alert.last_evaluated_at,
                   alert.version, acknowledgement.acknowledged_at
              FROM realtime_operational_alerts alert
              LEFT JOIN realtime_alert_acknowledgement_revisions acknowledgement
                ON acknowledgement.tenant_id = alert.tenant_id
               AND acknowledgement.alert_id = alert.id
               AND acknowledgement.profile_id = ?
               AND acknowledgement.acknowledged_observation_at = alert.last_observed_at
            """;

    private static final String VISIBLE_OPEN = """
               AND alert.state = 'OPEN'
               AND alert.source_occurred_at IS NOT NULL
               AND (
                   (alert.policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                       AND alert.source_event_id IS NULL)
                   OR (alert.policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
                       AND alert.source_event_id IS NOT NULL)
               )
            """;

    static final String LATEST_OPEN = SELECT_CURRENT_PROFILE + """
             WHERE alert.tenant_id = ?
            """ + VISIBLE_OPEN + """
             ORDER BY CASE alert.severity
                          WHEN 'CRITICAL' THEN 0
                          WHEN 'WARNING' THEN 1
                          ELSE 2
                      END,
                      alert.last_observed_at DESC,
                      alert.id ASC
             LIMIT 51
            """;

    static final String OPEN_BY_ID = SELECT_CURRENT_PROFILE + """
             WHERE alert.tenant_id = ?
               AND alert.id = ?
            """ + VISIBLE_OPEN;

    private RealtimeOperationalAlertQuerySql() {
    }
}
