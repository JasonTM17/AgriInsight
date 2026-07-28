-- flyway:executeInTransaction=false
-- These indexes support bounded worker pages. PostgreSQL forbids concurrent
-- index creation inside a transaction, so this migration must remain isolated.
CREATE INDEX CONCURRENTLY ix_outbox_events_alert_backlog
    ON outbox_events (tenant_id, occurred_at)
    WHERE status IN ('PENDING', 'LEASED');

CREATE INDEX CONCURRENTLY ix_outbox_events_alert_delivery_lag
    ON outbox_events (published_at, id)
    INCLUDE (tenant_id, occurred_at)
    WHERE status = 'PUBLISHED';

CREATE INDEX CONCURRENTLY ix_realtime_operational_alerts_unrecovered_dlt
    ON realtime_operational_alerts (last_observed_at, id)
    INCLUDE (tenant_id, source_event_id, source_occurred_at)
    WHERE policy_code = 'REALTIME_DLT_RECORD'
      AND state = 'OPEN'
      AND source_event_id IS NOT NULL;
