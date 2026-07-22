-- Live activities share-lock their season. A season cannot become terminal while
-- it still has PLANNED or STARTED activities. Both operations therefore contend
-- on the same season row and cannot commit an invalid parent/child state.

ALTER TABLE seasons DISABLE ROW LEVEL SECURITY;
ALTER TABLE activities DISABLE ROW LEVEL SECURITY;

DO $validation$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.activities AS activity
          JOIN public.seasons AS season
            ON season.tenant_id = activity.tenant_id
           AND season.farm_id = activity.farm_id
           AND season.field_id = activity.field_id
           AND season.id = activity.season_id
         WHERE activity.status IN ('PLANNED', 'STARTED')
           AND season.status NOT IN ('PLANNED', 'ACTIVE')
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install activity season lifecycle guards: live activity has terminal season',
            CONSTRAINT = 'live_activity_requires_live_season';
    END IF;
END
$validation$;

ALTER TABLE seasons ENABLE ROW LEVEL SECURITY;
ALTER TABLE seasons FORCE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities FORCE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_live_season_for_activity()
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
      FROM public.seasons AS season
     WHERE season.tenant_id = NEW.tenant_id
       AND season.farm_id = NEW.farm_id
       AND season.field_id = NEW.field_id
       AND season.id = NEW.season_id
       AND season.status IN ('PLANNED', 'ACTIVE')
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Live activity requires a planned or active parent season',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'live_activity_requires_live_season';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_live_season_for_activity() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_terminal_season_with_live_activities()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.status IN ('PLANNED', 'ACTIVE')
       AND NEW.status IN ('COMPLETED', 'CANCELLED')
       AND EXISTS (
            SELECT 1
              FROM public.activities AS activity
             WHERE activity.tenant_id = OLD.tenant_id
               AND activity.farm_id = OLD.farm_id
               AND activity.field_id = OLD.field_id
               AND activity.season_id = OLD.id
               AND activity.status IN ('PLANNED', 'STARTED')
       ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Season transition requires all live activities to be closed',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'season_transition_requires_closed_activities';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_terminal_season_with_live_activities() FROM PUBLIC;

CREATE TRIGGER activities_live_season_guard
    BEFORE INSERT OR UPDATE OF tenant_id, farm_id, field_id, season_id, status ON activities
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_live_season_for_activity();

CREATE TRIGGER seasons_live_activity_guard
    BEFORE UPDATE OF status ON seasons
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_terminal_season_with_live_activities();
