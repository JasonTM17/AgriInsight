-- flyway:executeInTransaction=false
-- Precondition: ix_outbox_events_alert_backlog must be absent.
-- If a failed build left it invalid, run DROP INDEX CONCURRENTLY ix_outbox_events_alert_backlog before Flyway repair/retry.
-- A valid existing index requires operator reconciliation of Flyway history; do not retry this migration.
-- This non-transactional migration needs session settings; reset them after a successful build.
SET lock_timeout = '5s';
SET statement_timeout = '15min';

CREATE INDEX CONCURRENTLY ix_outbox_events_alert_backlog
    ON outbox_events (tenant_id, occurred_at)
    WHERE status IN ('PENDING', 'LEASED');

RESET statement_timeout;
RESET lock_timeout;
