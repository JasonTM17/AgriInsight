-- Live operational records lock their active field and crop parents. Parent lifecycle
-- stores take FOR UPDATE in a separate statement so READ COMMITTED refreshes before
-- evaluating dependencies after either side of a race waits.

ALTER TABLE fields DISABLE ROW LEVEL SECURITY;
ALTER TABLE crops DISABLE ROW LEVEL SECURITY;
ALTER TABLE seasons DISABLE ROW LEVEL SECURITY;
ALTER TABLE activities DISABLE ROW LEVEL SECURITY;

DO $validation$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.fields AS field
         WHERE NOT field.active
           AND (
                EXISTS (
                    SELECT 1 FROM public.seasons AS season
                     WHERE season.tenant_id = field.tenant_id
                       AND season.farm_id = field.farm_id
                       AND season.field_id = field.id
                       AND season.status IN ('PLANNED', 'ACTIVE')
                ) OR EXISTS (
                    SELECT 1 FROM public.activities AS activity
                     WHERE activity.tenant_id = field.tenant_id
                       AND activity.farm_id = field.farm_id
                       AND activity.field_id = field.id
                       AND activity.status IN ('PLANNED', 'STARTED')
                )
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install field and crop lifecycle guards: inactive field has live dependencies',
            CONSTRAINT = 'inactive_field_has_live_dependencies';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.crops AS crop
         WHERE NOT crop.active
           AND EXISTS (
                SELECT 1 FROM public.seasons AS season
                 WHERE season.tenant_id = crop.tenant_id
                   AND season.crop_id = crop.id
                   AND season.status IN ('PLANNED', 'ACTIVE')
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install field and crop lifecycle guards: inactive crop has live seasons',
            CONSTRAINT = 'inactive_crop_has_live_seasons';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.seasons AS season
          JOIN public.fields AS field
            ON field.tenant_id = season.tenant_id
           AND field.farm_id = season.farm_id
           AND field.id = season.field_id
         WHERE season.status IN ('PLANNED', 'ACTIVE')
           AND season.planted_area_hectares > field.area_hectares
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install field and crop lifecycle guards: live season exceeds field area',
            CONSTRAINT = 'live_season_exceeds_field_area';
    END IF;
END
$validation$;

ALTER TABLE fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE crops ENABLE ROW LEVEL SECURITY;
ALTER TABLE seasons ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_active_field_and_crop_for_live_season()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    field_area NUMERIC(14, 4);
BEGIN
    IF NEW.status NOT IN ('PLANNED', 'ACTIVE') THEN
        RETURN NEW;
    END IF;

    SELECT field.area_hectares
      INTO field_area
      FROM public.fields AS field
     WHERE field.tenant_id = NEW.tenant_id
       AND field.farm_id = NEW.farm_id
       AND field.id = NEW.field_id
       AND field.active
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live season requires an active parent field',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_season_requires_active_field';
    END IF;

    IF NEW.planted_area_hectares > field_area THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live season planted area cannot exceed field area',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_season_within_field_area';
    END IF;

    PERFORM 1
      FROM public.crops AS crop
     WHERE crop.tenant_id = NEW.tenant_id
       AND crop.id = NEW.crop_id
       AND crop.active
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live season requires an active parent crop',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_season_requires_active_crop';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_field_and_crop_for_live_season() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.lock_active_field_for_live_activity()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF NEW.status NOT IN ('PLANNED', 'STARTED') THEN
        RETURN NEW;
    END IF;

    PERFORM 1
      FROM public.fields AS field
     WHERE field.tenant_id = NEW.tenant_id
       AND field.farm_id = NEW.farm_id
       AND field.id = NEW.field_id
       AND field.active
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live activity requires an active parent field',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_activity_requires_active_field';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_field_for_live_activity() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_field_change_with_live_dependencies()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND (
        EXISTS (
            SELECT 1 FROM public.seasons AS season
             WHERE season.tenant_id = OLD.tenant_id
               AND season.farm_id = OLD.farm_id
               AND season.field_id = OLD.id
               AND season.status IN ('PLANNED', 'ACTIVE')
        ) OR EXISTS (
            SELECT 1 FROM public.activities AS activity
             WHERE activity.tenant_id = OLD.tenant_id
               AND activity.farm_id = OLD.farm_id
               AND activity.field_id = OLD.id
               AND activity.status IN ('PLANNED', 'STARTED')
        )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Field deactivation requires all live dependencies to be closed',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'field_deactivation_requires_closed_dependencies';
    END IF;

    IF NEW.area_hectares IS DISTINCT FROM OLD.area_hectares AND EXISTS (
        SELECT 1 FROM public.seasons AS season
         WHERE season.tenant_id = OLD.tenant_id
           AND season.farm_id = OLD.farm_id
           AND season.field_id = OLD.id
           AND season.status IN ('PLANNED', 'ACTIVE')
           AND season.planted_area_hectares > NEW.area_hectares
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Field area cannot be smaller than a live season planted area',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'field_area_covers_live_seasons';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_field_change_with_live_dependencies() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_crop_deactivation_with_live_seasons()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND EXISTS (
        SELECT 1 FROM public.seasons AS season
         WHERE season.tenant_id = OLD.tenant_id
           AND season.crop_id = OLD.id
           AND season.status IN ('PLANNED', 'ACTIVE')
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Crop deactivation requires all live seasons to be closed',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'crop_deactivation_requires_closed_seasons';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_crop_deactivation_with_live_seasons() FROM PUBLIC;

CREATE TRIGGER seasons_active_field_crop_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, field_id, crop_id, planted_area_hectares, status ON seasons
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_field_and_crop_for_live_season();

CREATE TRIGGER activities_active_field_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, field_id, status ON activities
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_field_for_live_activity();

CREATE TRIGGER fields_lifecycle_dependency_guard
    BEFORE UPDATE OF active, area_hectares ON fields
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_field_change_with_live_dependencies();

CREATE TRIGGER crops_lifecycle_dependency_guard
    BEFORE UPDATE OF active ON crops
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_crop_deactivation_with_live_seasons();
