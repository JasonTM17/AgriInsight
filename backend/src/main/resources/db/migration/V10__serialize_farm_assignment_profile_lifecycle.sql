-- Active farm assignments require an active tenant profile. Profile lifecycle and
-- assignment creation lock the same parent row so both transaction orders are safe.
ALTER TABLE user_profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments DISABLE ROW LEVEL SECURITY;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.user_farm_assignments AS assignment
          JOIN public.user_profiles AS profile
            ON profile.tenant_id = assignment.tenant_id
           AND profile.id = assignment.user_profile_id
         WHERE assignment.revoked_at IS NULL
           AND NOT profile.active
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install farm assignment profile lifecycle guards while inactive profiles have active assignments',
            CONSTRAINT = 'active_farm_assignment_requires_active_profile';
    END IF;
END
$migration$;

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_active_profile_for_farm_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    profile_active BOOLEAN;
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
                MESSAGE = 'Active farm assignment requires an active tenant profile',
                TABLE = TG_TABLE_NAME,
                CONSTRAINT = 'active_farm_assignment_requires_active_profile';
        END IF;
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_profile_for_farm_assignment() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_profile_deactivation_with_active_farm_assignments()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND EXISTS (
        SELECT 1
          FROM public.user_farm_assignments AS assignment
         WHERE assignment.tenant_id = OLD.tenant_id
           AND assignment.user_profile_id = OLD.id
           AND assignment.revoked_at IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Tenant profile deactivation requires active farm assignments to be revoked',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'profile_deactivation_requires_revoked_farm_assignments';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_profile_deactivation_with_active_farm_assignments() FROM PUBLIC;

CREATE TRIGGER user_farm_assignments_active_profile_guard
    BEFORE INSERT OR UPDATE OF tenant_id, user_profile_id, revoked_at ON user_farm_assignments
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_profile_for_farm_assignment();

CREATE TRIGGER user_profiles_farm_assignment_deactivation_guard
    BEFORE UPDATE OF active ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_profile_deactivation_with_active_farm_assignments();
