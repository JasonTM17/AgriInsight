-- flyway:executeInTransaction=false
-- Precondition: ix_realtime_operational_alerts_tenant_open_feed must be absent.
-- If a failed build left it invalid, run DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_tenant_open_feed before Flyway repair/retry.
-- A valid existing index requires operator reconciliation of Flyway history; do not retry this migration.
-- This non-transactional migration needs session settings; reset them after a successful build.
SET lock_timeout = '5s';
SET statement_timeout = '15min';

CREATE INDEX CONCURRENTLY ix_realtime_operational_alerts_tenant_open_feed
    ON realtime_operational_alerts (
        tenant_id,
        (CASE severity
            WHEN 'CRITICAL' THEN 0
            WHEN 'WARNING' THEN 1
            ELSE 2
        END),
        last_observed_at DESC,
        id ASC
    )
    INCLUDE (
        policy_code,
        severity,
        source_event_id,
        source_occurred_at,
        opened_at,
        last_evaluated_at
    )
    WHERE state = 'OPEN'
      AND source_occurred_at IS NOT NULL
      AND (
          (policy_code = 'OUTBOX_PUBLISH_BACKLOG' AND source_event_id IS NULL)
          OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
              AND source_event_id IS NOT NULL)
      );

RESET statement_timeout;
RESET lock_timeout;
