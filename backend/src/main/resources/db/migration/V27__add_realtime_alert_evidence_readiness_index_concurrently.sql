-- flyway:executeInTransaction=false
-- Precondition: ix_realtime_operational_alerts_invalid_source_evidence must be absent.
-- If a failed build left it invalid, run DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_invalid_source_evidence before Flyway repair/retry.
-- A valid existing index requires operator reconciliation of Flyway history; do not retry this migration.
-- This non-transactional migration needs session settings; reset them after a successful build.
SET lock_timeout = '5s';
SET statement_timeout = '15min';

CREATE INDEX CONCURRENTLY ix_realtime_operational_alerts_invalid_source_evidence
    ON realtime_operational_alerts (id)
    WHERE (
        source_occurred_at IS NULL
        OR (policy_code = 'OUTBOX_PUBLISH_BACKLOG'
            AND source_event_id IS NOT NULL)
        OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
            AND source_event_id IS NULL)
    );

RESET statement_timeout;
RESET lock_timeout;
