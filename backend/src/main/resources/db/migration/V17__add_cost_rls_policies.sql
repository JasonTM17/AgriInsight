CREATE FUNCTION agriinsight_security.operating_cost_access(
    p_target_type TEXT,
    p_farm_id UUID,
    p_field_id UUID,
    p_season_id UUID,
    p_activity_id UUID,
    p_write BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $function$
    WITH target AS (
        SELECT CASE p_target_type
            WHEN 'FARM' THEN p_farm_id
            WHEN 'FIELD' THEN (
                SELECT field_row.farm_id
                  FROM public.fields AS field_row
                 WHERE field_row.tenant_id =
                       agriinsight_security.app_current_tenant_id()
                   AND field_row.id = p_field_id)
            WHEN 'SEASON' THEN (
                SELECT season_row.farm_id
                  FROM public.seasons AS season_row
                 WHERE season_row.tenant_id =
                       agriinsight_security.app_current_tenant_id()
                   AND season_row.id = p_season_id)
            WHEN 'ACTIVITY' THEN (
                SELECT activity_row.farm_id
                  FROM public.activities AS activity_row
                 WHERE activity_row.tenant_id =
                       agriinsight_security.app_current_tenant_id()
                   AND activity_row.id = p_activity_id)
            ELSE NULL
        END AS resolved_farm_id
    )
    SELECT EXISTS (
        SELECT 1
          FROM public.user_roles AS role_assignment
          JOIN public.role_permissions AS role_permission
            ON role_permission.role_code = role_assignment.role_code
           AND role_permission.permission_code =
               CASE WHEN p_write THEN 'COST_MANAGE' ELSE 'COST_READ' END
          CROSS JOIN target
         WHERE role_assignment.tenant_id =
               agriinsight_security.app_current_tenant_id()
           AND role_assignment.user_profile_id =
               agriinsight_security.app_current_profile_id()
           AND role_assignment.revoked_at IS NULL
           AND (
               role_assignment.role_code = 'TENANT_ADMIN'
               OR (NOT p_write AND role_assignment.role_code IN (
                   'EXECUTIVE', 'DATA_ANALYST'))
               OR (NOT p_write
                   AND role_assignment.role_code = 'FARM_MANAGER'
                   AND p_target_type <> 'TENANT'
                   AND target.resolved_farm_id IS NOT NULL
                   AND EXISTS (
                       SELECT 1
                         FROM public.user_farm_assignments AS assignment
                        WHERE assignment.tenant_id = role_assignment.tenant_id
                          AND assignment.user_profile_id =
                              role_assignment.user_profile_id
                          AND assignment.farm_id = target.resolved_farm_id
                          AND assignment.revoked_at IS NULL))
           )
    )
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.operating_cost_access(
    TEXT, UUID, UUID, UUID, UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION agriinsight_security.operating_cost_access(
    TEXT, UUID, UUID, UUID, UUID, BOOLEAN)
    TO agriinsight_runtime, agriinsight_migrator;

ALTER TABLE operating_cost_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE operating_cost_entries FORCE ROW LEVEL SECURITY;

CREATE POLICY runtime_operating_cost_read ON operating_cost_entries
    FOR SELECT TO agriinsight_runtime
    USING (
        tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.operating_cost_access(
            target_type, farm_id, field_id, season_id, activity_id, FALSE)
    );

CREATE POLICY runtime_operating_cost_insert ON operating_cost_entries
    FOR INSERT TO agriinsight_runtime
    WITH CHECK (
        tenant_id = agriinsight_security.app_current_tenant_id()
        AND agriinsight_security.operating_cost_access(
            target_type, farm_id, field_id, season_id, activity_id, TRUE)
    );

CREATE POLICY migration_tenant_isolation ON operating_cost_entries
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());
