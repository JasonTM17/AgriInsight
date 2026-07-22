ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;

CREATE POLICY runtime_outbox_insert ON outbox_events
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY integration_outbox_read ON outbox_events
    FOR SELECT TO agriinsight_integration
    USING (TRUE);

CREATE POLICY integration_outbox_lease ON outbox_events
    FOR UPDATE TO agriinsight_integration
    USING (TRUE)
    WITH CHECK (TRUE);

CREATE POLICY migration_outbox_access ON outbox_events
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);
