CREATE TABLE realtime_operational_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    policy_code VARCHAR(64) COLLATE "C" NOT NULL,
    dedupe_key VARCHAR(64) COLLATE "C" NOT NULL,
    severity VARCHAR(16) COLLATE "C" NOT NULL,
    state VARCHAR(16) COLLATE "C" NOT NULL DEFAULT 'OPEN',
    source_event_id UUID,
    source_occurred_at TIMESTAMPTZ,
    opened_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    clean_since TIMESTAMPTZ,
    clean_scan_count INTEGER NOT NULL DEFAULT 0,
    last_evaluated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_realtime_operational_alerts_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_realtime_operational_alerts_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT ux_realtime_operational_alerts_identity
        UNIQUE (tenant_id, policy_code, dedupe_key),
    CONSTRAINT realtime_operational_alerts_policy_code
        CHECK (policy_code IN (
            'OUTBOX_PUBLISH_BACKLOG',
            'REALTIME_DELIVERY_LAG',
            'REALTIME_DLT_RECORD'
        )),
    CONSTRAINT realtime_operational_alerts_dedupe_key_format
        CHECK (dedupe_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT realtime_operational_alerts_severity
        CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT realtime_operational_alerts_state
        CHECK (state IN ('OPEN', 'RESOLVED')),
    CONSTRAINT realtime_operational_alerts_observation_order
        CHECK (last_observed_at >= opened_at),
    CONSTRAINT realtime_operational_alerts_resolution_state
        CHECK (
            (state = 'OPEN' AND resolved_at IS NULL)
            OR (state = 'RESOLVED' AND resolved_at IS NOT NULL)
        ),
    CONSTRAINT realtime_operational_alerts_clean_scan_count
        CHECK (clean_scan_count >= 0),
    CONSTRAINT realtime_operational_alerts_version_nonnegative
        CHECK (version >= 0)
);

CREATE INDEX ix_realtime_operational_alerts_tenant_open
    ON realtime_operational_alerts (tenant_id, state, last_observed_at DESC, id)
    INCLUDE (policy_code, severity, source_event_id, resolved_at);

CREATE INDEX ix_realtime_operational_alerts_policy_open
    ON realtime_operational_alerts (policy_code, state, last_evaluated_at, id);

CREATE TABLE realtime_alert_acknowledgement_revisions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alert_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    acknowledged_observation_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_realtime_alert_acknowledgement_revisions_alert
        FOREIGN KEY (tenant_id, alert_id)
        REFERENCES realtime_operational_alerts (tenant_id, id),
    CONSTRAINT fk_realtime_alert_acknowledgement_revisions_profile
        FOREIGN KEY (tenant_id, profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT ux_realtime_alert_acknowledgement_revisions_observation
        UNIQUE (tenant_id, alert_id, profile_id, acknowledged_observation_at)
);

CREATE INDEX ix_realtime_alert_acknowledgement_revisions_current
    ON realtime_alert_acknowledgement_revisions (
        tenant_id, alert_id, profile_id, acknowledged_observation_at DESC, id DESC);

CREATE FUNCTION agriinsight_security.assert_realtime_alert_acknowledgement_observation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $function$
DECLARE
    current_observation TIMESTAMPTZ;
BEGIN
    SELECT alert.last_observed_at
      INTO current_observation
      FROM public.realtime_operational_alerts AS alert
     WHERE alert.tenant_id = NEW.tenant_id
       AND alert.id = NEW.alert_id
     FOR SHARE;

    IF NOT FOUND OR current_observation IS DISTINCT FROM NEW.acknowledged_observation_at THEN
        RAISE EXCEPTION 'Acknowledgement observation must match the alert current observation'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.assert_realtime_alert_acknowledgement_observation()
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.assert_realtime_alert_acknowledgement_observation()
    TO agriinsight_runtime, agriinsight_migrator;

CREATE TRIGGER realtime_alert_acknowledgement_revisions_observation_guard
    BEFORE INSERT ON realtime_alert_acknowledgement_revisions
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.assert_realtime_alert_acknowledgement_observation();

CREATE FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
    p_tenant_id UUID,
    p_profile_id UUID,
    p_alert_id UUID,
    p_revision_id UUID,
    p_acknowledged_at TIMESTAMPTZ
)
RETURNS TABLE (
    acknowledged_observation_at TIMESTAMPTZ,
    created BOOLEAN
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $function$
DECLARE
    current_observation TIMESTAMPTZ;
    inserted_count INTEGER;
BEGIN
    IF p_tenant_id IS NULL
       OR p_profile_id IS NULL
       OR p_alert_id IS NULL
       OR p_revision_id IS NULL
       OR p_acknowledged_at IS NULL THEN
        RAISE EXCEPTION 'Acknowledgement inputs are required' USING ERRCODE = '22004';
    END IF;
    IF p_tenant_id IS DISTINCT FROM agriinsight_security.app_current_tenant_id()
       OR p_profile_id IS DISTINCT FROM agriinsight_security.app_current_profile_id() THEN
        RAISE EXCEPTION 'Acknowledgement scope does not match the current runtime context'
            USING ERRCODE = '42501';
    END IF;
    PERFORM 1
      FROM public.user_profiles AS profile
     WHERE profile.tenant_id = p_tenant_id
       AND profile.id = p_profile_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Acknowledgement profile is not available in the tenant'
            USING ERRCODE = '42501';
    END IF;

    SELECT alert.last_observed_at
      INTO current_observation
      FROM public.realtime_operational_alerts AS alert
     WHERE alert.tenant_id = p_tenant_id
       AND alert.id = p_alert_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    INSERT INTO public.realtime_alert_acknowledgement_revisions (
        id, tenant_id, alert_id, profile_id, acknowledged_observation_at, acknowledged_at)
    VALUES (
        p_revision_id, p_tenant_id, p_alert_id, p_profile_id,
        current_observation, p_acknowledged_at)
    ON CONFLICT (tenant_id, alert_id, profile_id, acknowledged_observation_at) DO NOTHING;
    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    RETURN QUERY SELECT current_observation, inserted_count = 1;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
    UUID, UUID, UUID, UUID, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
    UUID, UUID, UUID, UUID, TIMESTAMPTZ)
    TO agriinsight_runtime, agriinsight_migrator;

ALTER TABLE realtime_operational_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_operational_alerts FORCE ROW LEVEL SECURITY;
ALTER TABLE realtime_alert_acknowledgement_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_alert_acknowledgement_revisions FORCE ROW LEVEL SECURITY;

CREATE POLICY integration_realtime_operational_alerts_select ON realtime_operational_alerts
    FOR SELECT TO agriinsight_integration
    USING (TRUE);
CREATE POLICY integration_realtime_operational_alerts_insert ON realtime_operational_alerts
    FOR INSERT TO agriinsight_integration
    WITH CHECK (TRUE);
CREATE POLICY integration_realtime_operational_alerts_update ON realtime_operational_alerts
    FOR UPDATE TO agriinsight_integration
    USING (TRUE)
    WITH CHECK (TRUE);
CREATE POLICY runtime_realtime_operational_alerts_select ON realtime_operational_alerts
    FOR SELECT TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id());
CREATE POLICY migration_realtime_operational_alerts_access ON realtime_operational_alerts
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);

CREATE POLICY runtime_realtime_alert_acknowledgement_revisions_select
    ON realtime_alert_acknowledgement_revisions
    FOR SELECT TO agriinsight_runtime
    USING (
        tenant_id = agriinsight_security.app_current_tenant_id()
        AND profile_id = agriinsight_security.app_current_profile_id()
    );
CREATE POLICY runtime_realtime_alert_acknowledgement_revisions_insert
    ON realtime_alert_acknowledgement_revisions
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (
        tenant_id = agriinsight_security.app_current_tenant_id()
        AND profile_id = agriinsight_security.app_current_profile_id()
    );
CREATE POLICY migration_realtime_alert_acknowledgement_revisions_access
    ON realtime_alert_acknowledgement_revisions
    FOR ALL TO agriinsight_migrator
    USING (TRUE)
    WITH CHECK (TRUE);

INSERT INTO permissions (code, display_name) VALUES
    ('REALTIME_ALERT_READ', 'Read tenant realtime operational alerts'),
    ('REALTIME_ALERT_ACKNOWLEDGE', 'Acknowledge tenant realtime operational alerts');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'REALTIME_ALERT_READ'),
    ('TENANT_ADMIN', 'REALTIME_ALERT_ACKNOWLEDGE'),
    ('EXECUTIVE', 'REALTIME_ALERT_READ'),
    ('EXECUTIVE', 'REALTIME_ALERT_ACKNOWLEDGE'),
    ('DATA_ANALYST', 'REALTIME_ALERT_READ'),
    ('DATA_ANALYST', 'REALTIME_ALERT_ACKNOWLEDGE');
