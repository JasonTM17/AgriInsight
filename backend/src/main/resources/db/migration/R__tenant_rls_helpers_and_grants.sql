SET ROLE agriinsight_identity_definer;

CREATE OR REPLACE FUNCTION agriinsight_security.resolve_identity_bootstrap(
    p_issuer TEXT,
    p_subject TEXT
)
RETURNS TABLE (
    profile_id UUID,
    tenant_id UUID,
    profile_active BOOLEAN,
    tenant_active BOOLEAN
)
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
    SELECT
        identity_row.user_profile_id,
        identity_row.tenant_id,
        profile.active,
        tenant.active
    FROM public.external_identities AS identity_row
    JOIN public.user_profiles AS profile
      ON profile.tenant_id = identity_row.tenant_id
     AND profile.id = identity_row.user_profile_id
    JOIN public.tenants AS tenant
      ON tenant.id = identity_row.tenant_id
    WHERE identity_row.issuer = p_issuer
      AND identity_row.subject = p_subject
      AND identity_row.active = TRUE
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT)
    TO agriinsight_runtime;

RESET ROLE;

CREATE OR REPLACE FUNCTION agriinsight_security.link_external_identity(
    p_identity_id UUID,
    p_profile_id UUID,
    p_issuer TEXT,
    p_subject TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    resolved_tenant UUID;
BEGIN
    resolved_tenant := agriinsight_security.app_current_tenant_id();
    IF resolved_tenant IS NULL THEN
        RAISE EXCEPTION 'Tenant context is required'
            USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.external_identities (
        id,
        tenant_id,
        user_profile_id,
        issuer,
        subject
    )
    SELECT
        p_identity_id,
        resolved_tenant,
        profile.id,
        p_issuer,
        p_subject
    FROM public.user_profiles AS profile
    WHERE profile.tenant_id = resolved_tenant
      AND profile.id = p_profile_id
      AND profile.active = TRUE;

    RETURN FOUND;
END
$function$;

CREATE OR REPLACE FUNCTION agriinsight_security.unlink_external_identity(
    p_profile_id UUID,
    p_identity_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    resolved_tenant UUID;
BEGIN
    resolved_tenant := agriinsight_security.app_current_tenant_id();
    IF resolved_tenant IS NULL THEN
        RAISE EXCEPTION 'Tenant context is required'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.external_identities
       SET active = FALSE,
           version = version + 1,
           updated_at = CURRENT_TIMESTAMP
     WHERE tenant_id = resolved_tenant
       AND user_profile_id = p_profile_id
       AND id = p_identity_id
       AND active = TRUE;

    RETURN FOUND;
END
$function$;

REVOKE ALL ON SCHEMA agriinsight_security FROM PUBLIC;
GRANT USAGE ON SCHEMA agriinsight_security TO agriinsight_runtime;
GRANT USAGE, CREATE ON SCHEMA agriinsight_security TO agriinsight_identity_definer;

REVOKE ALL ON FUNCTION agriinsight_security.app_current_tenant_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION agriinsight_security.link_external_identity(UUID, UUID, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION agriinsight_security.unlink_external_identity(UUID, UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION agriinsight_security.app_current_tenant_id()
    TO agriinsight_runtime;
GRANT EXECUTE ON FUNCTION agriinsight_security.link_external_identity(UUID, UUID, TEXT, TEXT)
    TO agriinsight_runtime;
GRANT EXECUTE ON FUNCTION agriinsight_security.unlink_external_identity(UUID, UUID)
    TO agriinsight_runtime;

REVOKE ALL ON tenants FROM PUBLIC;
REVOKE ALL ON user_profiles FROM PUBLIC;
REVOKE ALL ON external_identities FROM PUBLIC;
REVOKE ALL ON roles FROM PUBLIC;
REVOKE ALL ON permissions FROM PUBLIC;
REVOKE ALL ON user_roles FROM PUBLIC;
REVOKE ALL ON role_permissions FROM PUBLIC;
REVOKE ALL ON api_command_records FROM PUBLIC;
REVOKE ALL ON tenant_audit_events FROM PUBLIC;
REVOKE ALL ON flyway_schema_history FROM PUBLIC;

REVOKE ALL ON external_identities FROM agriinsight_runtime;

GRANT SELECT ON tenants TO agriinsight_runtime;
GRANT SELECT, INSERT, UPDATE ON user_profiles TO agriinsight_runtime;
GRANT SELECT ON roles TO agriinsight_runtime;
GRANT SELECT ON permissions TO agriinsight_runtime;
GRANT SELECT, INSERT, UPDATE ON user_roles TO agriinsight_runtime;
GRANT SELECT ON role_permissions TO agriinsight_runtime;
GRANT SELECT, INSERT, UPDATE ON api_command_records TO agriinsight_runtime;
GRANT INSERT ON tenant_audit_events TO agriinsight_runtime;
GRANT SELECT ON flyway_schema_history TO agriinsight_runtime;

GRANT SELECT (id, tenant_id, user_profile_id, issuer, subject, active)
    ON external_identities TO agriinsight_identity_definer;
GRANT SELECT (id, tenant_id, active)
    ON user_profiles TO agriinsight_identity_definer;
GRANT SELECT (id, active)
    ON tenants TO agriinsight_identity_definer;

ALTER DEFAULT PRIVILEGES FOR ROLE agriinsight_migrator IN SCHEMA public
    REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE agriinsight_migrator IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE agriinsight_migrator IN SCHEMA agriinsight_security
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
