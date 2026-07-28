-- Legacy alert rows remain unvalidated; the operator backfill is required before
-- the isolated alert worker is enabled.
ALTER TABLE realtime_operational_alerts
    ADD CONSTRAINT realtime_operational_alerts_source_occurred_at_present
    CHECK (source_occurred_at IS NOT NULL) NOT VALID;

ALTER TABLE realtime_operational_alerts
    ADD CONSTRAINT realtime_operational_alerts_evidence_shape
    CHECK (
        (policy_code = 'OUTBOX_PUBLISH_BACKLOG' AND source_event_id IS NULL)
        OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
            AND source_event_id IS NOT NULL)
    ) NOT VALID;

CREATE TABLE realtime_operational_alert_scan_cursors (
    policy_code VARCHAR(64) COLLATE "C" PRIMARY KEY,
    cursor_tenant_id UUID,
    cursor_ordered_at TIMESTAMPTZ,
    cursor_ordered_id UUID,
    cycle_started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT realtime_operational_alert_scan_cursors_policy_code
        CHECK (policy_code IN (
            'OUTBOX_PUBLISH_BACKLOG',
            'REALTIME_DELIVERY_LAG',
            'REALTIME_DLT_RECORD'
        )),
    CONSTRAINT realtime_operational_alert_scan_cursors_shape
        CHECK (
            (policy_code = 'OUTBOX_PUBLISH_BACKLOG'
                AND cursor_tenant_id IS NOT NULL
                AND cursor_ordered_at IS NULL
                AND cursor_ordered_id IS NULL)
            OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
                AND cursor_tenant_id IS NULL
                AND cursor_ordered_at IS NOT NULL
                AND cursor_ordered_id IS NOT NULL)
        ),
    CONSTRAINT realtime_operational_alert_scan_cursors_cycle_order
        CHECK (cycle_started_at <= updated_at)
);

ALTER TABLE realtime_operational_alert_scan_cursors ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_operational_alert_scan_cursors FORCE ROW LEVEL SECURITY;

DROP POLICY integration_realtime_operational_alerts_select
    ON realtime_operational_alerts;
DROP POLICY integration_realtime_operational_alerts_insert
    ON realtime_operational_alerts;
DROP POLICY integration_realtime_operational_alerts_update
    ON realtime_operational_alerts;

CREATE POLICY alert_worker_tenants_select ON tenants
    FOR SELECT TO agriinsight_alert_worker
    USING (TRUE);

CREATE POLICY alert_worker_outbox_read ON outbox_events
    FOR SELECT TO agriinsight_alert_worker
    USING (TRUE);
CREATE POLICY alert_worker_realtime_event_receipts_read ON realtime_event_receipts
    FOR SELECT TO agriinsight_alert_worker
    USING (TRUE);

CREATE POLICY alert_worker_realtime_operational_alerts_select ON realtime_operational_alerts
    FOR SELECT TO agriinsight_alert_worker
    USING (TRUE);
CREATE POLICY alert_worker_realtime_operational_alerts_insert ON realtime_operational_alerts
    FOR INSERT TO agriinsight_alert_worker
    WITH CHECK (TRUE);
CREATE POLICY alert_worker_realtime_operational_alerts_update ON realtime_operational_alerts
    FOR UPDATE TO agriinsight_alert_worker
    USING (TRUE)
    WITH CHECK (TRUE);

CREATE POLICY alert_worker_realtime_operational_alert_scan_cursors_select
    ON realtime_operational_alert_scan_cursors
    FOR SELECT TO agriinsight_alert_worker
    USING (TRUE);
CREATE POLICY alert_worker_realtime_operational_alert_scan_cursors_insert
    ON realtime_operational_alert_scan_cursors
    FOR INSERT TO agriinsight_alert_worker
    WITH CHECK (TRUE);
CREATE POLICY alert_worker_realtime_operational_alert_scan_cursors_update
    ON realtime_operational_alert_scan_cursors
    FOR UPDATE TO agriinsight_alert_worker
    USING (TRUE)
    WITH CHECK (TRUE);
CREATE POLICY alert_worker_realtime_operational_alert_scan_cursors_delete
    ON realtime_operational_alert_scan_cursors
    FOR DELETE TO agriinsight_alert_worker
    USING (TRUE);
CREATE POLICY migration_realtime_operational_alert_scan_cursors_access
    ON realtime_operational_alert_scan_cursors
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);
