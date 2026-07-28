-- flyway:executeInTransaction=false
-- Precondition: ix_realtime_operational_alerts_unrecovered_dlt must be absent.
-- If a failed build left it invalid, run DROP INDEX CONCURRENTLY ix_realtime_operational_alerts_unrecovered_dlt before Flyway repair/retry.
-- A valid existing index requires operator reconciliation of Flyway history; do not retry this migration.
-- This non-transactional migration needs session settings; reset them after a successful build.
SET lock_timeout = '5s';
SET statement_timeout = '15min';

CREATE INDEX CONCURRENTLY ix_realtime_operational_alerts_unrecovered_dlt
    ON realtime_operational_alerts (last_observed_at, id)
    INCLUDE (tenant_id, source_event_id, source_occurred_at)
    WHERE policy_code = 'REALTIME_DLT_RECORD'
      AND state = 'OPEN'
      AND source_event_id IS NOT NULL;

RESET statement_timeout;
RESET lock_timeout;
