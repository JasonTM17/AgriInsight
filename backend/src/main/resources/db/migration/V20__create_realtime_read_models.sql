CREATE TABLE realtime_event_receipts (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    checksum VARCHAR(64) COLLATE "C" NOT NULL,
    topic VARCHAR(249) COLLATE "C" NOT NULL,
    partition_id INTEGER NOT NULL,
    broker_offset BIGINT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_realtime_event_receipts_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_realtime_event_receipts_tenant_event
        UNIQUE (tenant_id, event_id),
    CONSTRAINT ux_realtime_event_receipts_broker_coordinate
        UNIQUE (topic, partition_id, broker_offset),
    CONSTRAINT realtime_event_receipts_checksum_format
        CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT realtime_event_receipts_topic_format
        CHECK (topic ~ '^[A-Za-z0-9._-]{1,249}$' AND topic NOT IN ('.', '..')),
    CONSTRAINT realtime_event_receipts_partition_nonnegative
        CHECK (partition_id >= 0),
    CONSTRAINT realtime_event_receipts_offset_nonnegative
        CHECK (broker_offset >= 0)
);

CREATE INDEX ix_realtime_event_receipts_tenant_received
    ON realtime_event_receipts (tenant_id, received_at DESC, event_id);

CREATE TABLE realtime_aggregate_progress (
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) COLLATE "C" NOT NULL,
    aggregate_id UUID NOT NULL,
    last_version BIGINT NOT NULL,
    last_event_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, aggregate_type, aggregate_id),
    CONSTRAINT fk_realtime_aggregate_progress_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_realtime_aggregate_progress_event
        FOREIGN KEY (tenant_id, last_event_id)
        REFERENCES realtime_event_receipts (tenant_id, event_id),
    CONSTRAINT realtime_aggregate_progress_type_format
        CHECK (aggregate_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT realtime_aggregate_progress_version_nonnegative
        CHECK (last_version >= 0)
);

CREATE TABLE realtime_tenant_metrics (
    tenant_id UUID NOT NULL,
    event_type VARCHAR(160) COLLATE "C" NOT NULL,
    aggregate_type VARCHAR(64) COLLATE "C" NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 0,
    last_occurred_at TIMESTAMPTZ NOT NULL,
    last_processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, event_type),
    CONSTRAINT fk_realtime_tenant_metrics_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT realtime_tenant_metrics_event_type_format
        CHECK (event_type ~ '^AGRIINSIGHT\.OPERATIONAL\.[A-Z][A-Z0-9_]{0,63}\.COMMITTED$'),
    CONSTRAINT realtime_tenant_metrics_aggregate_type_format
        CHECK (aggregate_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT realtime_tenant_metrics_count_nonnegative
        CHECK (event_count >= 0)
);

ALTER TABLE realtime_event_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_event_receipts FORCE ROW LEVEL SECURITY;
ALTER TABLE realtime_aggregate_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_aggregate_progress FORCE ROW LEVEL SECURITY;
ALTER TABLE realtime_tenant_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_tenant_metrics FORCE ROW LEVEL SECURITY;

CREATE POLICY integration_realtime_event_receipts_select ON realtime_event_receipts
    FOR SELECT TO agriinsight_integration
    USING (TRUE);
CREATE POLICY integration_realtime_event_receipts_insert ON realtime_event_receipts
    FOR INSERT TO agriinsight_integration
    WITH CHECK (TRUE);
CREATE POLICY integration_realtime_event_receipts_update ON realtime_event_receipts
    FOR UPDATE TO agriinsight_integration
    USING (TRUE)
    WITH CHECK (TRUE);
CREATE POLICY migration_realtime_event_receipts_access ON realtime_event_receipts
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);

CREATE POLICY integration_realtime_aggregate_progress_select ON realtime_aggregate_progress
    FOR SELECT TO agriinsight_integration
    USING (TRUE);
CREATE POLICY integration_realtime_aggregate_progress_insert ON realtime_aggregate_progress
    FOR INSERT TO agriinsight_integration
    WITH CHECK (TRUE);
CREATE POLICY integration_realtime_aggregate_progress_update ON realtime_aggregate_progress
    FOR UPDATE TO agriinsight_integration
    USING (TRUE)
    WITH CHECK (TRUE);
CREATE POLICY migration_realtime_aggregate_progress_access ON realtime_aggregate_progress
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);

CREATE POLICY integration_realtime_tenant_metrics_select ON realtime_tenant_metrics
    FOR SELECT TO agriinsight_integration
    USING (TRUE);
CREATE POLICY integration_realtime_tenant_metrics_insert ON realtime_tenant_metrics
    FOR INSERT TO agriinsight_integration
    WITH CHECK (TRUE);
CREATE POLICY integration_realtime_tenant_metrics_update ON realtime_tenant_metrics
    FOR UPDATE TO agriinsight_integration
    USING (TRUE)
    WITH CHECK (TRUE);
CREATE POLICY runtime_realtime_tenant_metrics_select ON realtime_tenant_metrics
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_realtime_tenant_metrics_access ON realtime_tenant_metrics
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);

INSERT INTO permissions (code, display_name)
VALUES ('REALTIME_READ', 'Read tenant realtime summaries');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'REALTIME_READ'),
    ('EXECUTIVE', 'REALTIME_READ'),
    ('DATA_ANALYST', 'REALTIME_READ');
