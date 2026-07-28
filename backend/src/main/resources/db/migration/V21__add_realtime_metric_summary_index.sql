CREATE INDEX ix_realtime_tenant_metrics_summary
    ON realtime_tenant_metrics (tenant_id, last_processed_at DESC, event_type)
    INCLUDE (aggregate_type, event_count, last_occurred_at);
