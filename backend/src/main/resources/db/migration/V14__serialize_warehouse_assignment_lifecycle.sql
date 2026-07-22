-- Active warehouse assignments require active tenant profiles and warehouses.
-- Assignment creation and both parent lifecycle updates lock the same parent rows,
-- so either transaction order preserves the invariant.
ALTER TABLE user_profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE warehouses DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouse_assignments DISABLE ROW LEVEL SECURITY;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.user_warehouse_assignments AS assignment
          JOIN public.user_profiles AS profile
            ON profile.tenant_id = assignment.tenant_id
           AND profile.id = assignment.user_profile_id
          JOIN public.warehouses AS warehouse
            ON warehouse.tenant_id = assignment.tenant_id
           AND warehouse.id = assignment.warehouse_id
         WHERE assignment.revoked_at IS NULL
           AND (NOT profile.active OR NOT warehouse.active)
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install warehouse assignment lifecycle guards while active assignments have inactive targets',
            CONSTRAINT = 'active_warehouse_assignment_requires_active_targets';
    END IF;
END
$migration$;

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouses FORCE ROW LEVEL SECURITY;
ALTER TABLE user_warehouse_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouse_assignments FORCE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_active_warehouse_assignment_targets()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    profile_active BOOLEAN;
    warehouse_active BOOLEAN;
BEGIN
    IF NEW.revoked_at IS NULL THEN
        SELECT profile.active
          INTO profile_active
          FROM public.user_profiles AS profile
         WHERE profile.tenant_id = NEW.tenant_id
           AND profile.id = NEW.user_profile_id
         FOR SHARE;

        IF NOT FOUND OR NOT profile_active THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'Active warehouse assignment requires an active tenant profile',
                TABLE = TG_TABLE_NAME,
                CONSTRAINT = 'active_warehouse_assignment_requires_active_profile';
        END IF;

        SELECT warehouse.active
          INTO warehouse_active
          FROM public.warehouses AS warehouse
         WHERE warehouse.tenant_id = NEW.tenant_id
           AND warehouse.id = NEW.warehouse_id
         FOR SHARE;

        IF NOT FOUND OR NOT warehouse_active THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'Active warehouse assignment requires an active warehouse',
                TABLE = TG_TABLE_NAME,
                CONSTRAINT = 'active_warehouse_assignment_requires_active_warehouse';
        END IF;
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_warehouse_assignment_targets()
    FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_profile_deactivation_with_active_warehouse_assignments()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND EXISTS (
        SELECT 1
          FROM public.user_warehouse_assignments AS assignment
         WHERE assignment.tenant_id = OLD.tenant_id
           AND assignment.user_profile_id = OLD.id
           AND assignment.revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Tenant profile deactivation requires active warehouse assignments to be revoked',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'profile_deactivation_requires_revoked_warehouse_assignments';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION
    agriinsight_security.reject_profile_deactivation_with_active_warehouse_assignments()
    FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_warehouse_deactivation_with_active_assignments()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND EXISTS (
        SELECT 1
          FROM public.user_warehouse_assignments AS assignment
         WHERE assignment.tenant_id = OLD.tenant_id
           AND assignment.warehouse_id = OLD.id
           AND assignment.revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Warehouse deactivation requires active assignments to be revoked',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'warehouse_deactivation_requires_revoked_assignments';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION
    agriinsight_security.reject_warehouse_deactivation_with_active_assignments()
    FROM PUBLIC;

CREATE TRIGGER user_warehouse_assignments_active_targets_guard
    BEFORE INSERT OR UPDATE OF tenant_id, user_profile_id, warehouse_id, revoked_at
    ON user_warehouse_assignments
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_warehouse_assignment_targets();

CREATE TRIGGER user_profiles_warehouse_assignment_deactivation_guard
    BEFORE UPDATE OF active ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION
        agriinsight_security.reject_profile_deactivation_with_active_warehouse_assignments();

CREATE TRIGGER warehouses_assignment_deactivation_guard
    BEFORE UPDATE OF active ON warehouses
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_warehouse_deactivation_with_active_assignments();
