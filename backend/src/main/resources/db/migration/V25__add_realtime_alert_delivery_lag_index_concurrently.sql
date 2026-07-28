-- flyway:executeInTransaction=false
-- Precondition: ix_outbox_events_alert_delivery_lag must be absent.
-- If a failed build left it invalid, run DROP INDEX CONCURRENTLY ix_outbox_events_alert_delivery_lag before Flyway repair/retry.
-- A valid existing index requires operator reconciliation of Flyway history; do not retry this migration.
CREATE INDEX CONCURRENTLY ix_outbox_events_alert_delivery_lag
    ON outbox_events (published_at, id)
    INCLUDE (tenant_id, occurred_at)
    WHERE status = 'PUBLISHED';
