SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE OR REPLACE FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
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
    ON CONFLICT ON CONSTRAINT ux_realtime_alert_acknowledgement_revisions_observation
    DO NOTHING;
    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    RETURN QUERY SELECT current_observation, inserted_count = 1;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
    UUID, UUID, UUID, UUID, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.acknowledge_realtime_operational_alert(
    UUID, UUID, UUID, UUID, TIMESTAMPTZ)
    TO agriinsight_runtime, agriinsight_migrator;
