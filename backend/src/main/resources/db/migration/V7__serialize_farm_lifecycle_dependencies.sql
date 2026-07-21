-- Live farm children share-lock their parent. Farm lifecycle takes FOR UPDATE in a
-- separate statement, so READ COMMITTED refreshes before evaluating dependencies.

-- Migrations run as the table owner without tenant context. Disable RLS only inside
-- this transactional migration so the upgrade audit can inspect every tenant. A
-- raised exception rolls these ALTER statements back with the migration.
ALTER TABLE farms DISABLE ROW LEVEL SECURITY;
ALTER TABLE fields DISABLE ROW LEVEL SECURITY;
ALTER TABLE seasons DISABLE ROW LEVEL SECURITY;
ALTER TABLE activities DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments DISABLE ROW LEVEL SECURITY;

DO $validation$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.farms AS farm
         WHERE NOT farm.active
           AND (
                EXISTS (
                    SELECT 1 FROM public.fields AS child_field
                     WHERE child_field.tenant_id = farm.tenant_id
                       AND child_field.farm_id = farm.id
                       AND child_field.active
                ) OR EXISTS (
                    SELECT 1 FROM public.seasons AS season
                     WHERE season.tenant_id = farm.tenant_id
                       AND season.farm_id = farm.id
                       AND season.status IN ('PLANNED', 'ACTIVE')
                ) OR EXISTS (
                    SELECT 1 FROM public.activities AS activity
                     WHERE activity.tenant_id = farm.tenant_id
                       AND activity.farm_id = farm.id
                       AND activity.status IN ('PLANNED', 'STARTED')
                ) OR EXISTS (
                    SELECT 1 FROM public.user_farm_assignments AS assignment
                     WHERE assignment.tenant_id = farm.tenant_id
                       AND assignment.farm_id = farm.id
                       AND assignment.revoked_at IS NULL
                )
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install farm lifecycle guards: inactive farm has live dependents',
            CONSTRAINT = 'inactive_farm_has_live_dependencies';
    END IF;
END
$validation$;

ALTER TABLE farms ENABLE ROW LEVEL SECURITY;
ALTER TABLE fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE seasons ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_farm_assignments ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_active_farm_for_live_dependency()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    dependency JSONB := to_jsonb(NEW);
    dependency_is_live BOOLEAN;
    dependency_tenant_id UUID;
    dependency_farm_id UUID;
BEGIN
    dependency_is_live := CASE TG_ARGV[0]
        WHEN 'ACTIVE_FLAG' THEN COALESCE((dependency ->> 'active')::BOOLEAN, FALSE)
        WHEN 'SEASON_STATUS' THEN dependency ->> 'status' IN ('PLANNED', 'ACTIVE')
        WHEN 'ACTIVITY_STATUS' THEN dependency ->> 'status' IN ('PLANNED', 'STARTED')
        WHEN 'ACTIVE_ASSIGNMENT' THEN dependency ->> 'revoked_at' IS NULL
        ELSE FALSE
    END;

    IF NOT dependency_is_live THEN
        RETURN NEW;
    END IF;

    dependency_tenant_id := (dependency ->> 'tenant_id')::UUID;
    dependency_farm_id := (dependency ->> 'farm_id')::UUID;

    PERFORM 1
      FROM public.farms AS parent_farm
     WHERE parent_farm.tenant_id = dependency_tenant_id
       AND parent_farm.id = dependency_farm_id
       AND parent_farm.active
       FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live farm dependency requires an active parent farm',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_farm_dependency_requires_active_parent';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_farm_for_live_dependency() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_farm_deactivation_with_live_dependencies()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND (
        EXISTS (
            SELECT 1 FROM public.fields AS child_field
             WHERE child_field.tenant_id = OLD.tenant_id
               AND child_field.farm_id = OLD.id
               AND child_field.active
        ) OR EXISTS (
            SELECT 1 FROM public.seasons AS season
             WHERE season.tenant_id = OLD.tenant_id
               AND season.farm_id = OLD.id
               AND season.status IN ('PLANNED', 'ACTIVE')
        ) OR EXISTS (
            SELECT 1 FROM public.activities AS activity
             WHERE activity.tenant_id = OLD.tenant_id
               AND activity.farm_id = OLD.id
               AND activity.status IN ('PLANNED', 'STARTED')
        ) OR EXISTS (
            SELECT 1 FROM public.user_farm_assignments AS assignment
             WHERE assignment.tenant_id = OLD.tenant_id
               AND assignment.farm_id = OLD.id
               AND assignment.revoked_at IS NULL
        )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Farm deactivation requires all live dependencies to be closed',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'farm_deactivation_requires_closed_dependencies';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_farm_deactivation_with_live_dependencies() FROM PUBLIC;

CREATE TRIGGER farms_deactivation_dependency_guard
    BEFORE UPDATE OF active ON farms
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_farm_deactivation_with_live_dependencies();

CREATE TRIGGER fields_active_farm_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, active ON fields
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_farm_for_live_dependency('ACTIVE_FLAG');

CREATE TRIGGER seasons_active_farm_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, status ON seasons
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_farm_for_live_dependency('SEASON_STATUS');

CREATE TRIGGER activities_active_farm_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, status ON activities
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_farm_for_live_dependency('ACTIVITY_STATUS');

CREATE TRIGGER user_farm_assignments_active_farm_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, revoked_at ON user_farm_assignments
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_farm_for_live_dependency('ACTIVE_ASSIGNMENT');
