-- Active responsibilities and assignments keep their employee parent active.
-- Parent lifecycle stores lock the employee in a separate statement so a waiting
-- READ COMMITTED deactivation refreshes its dependency checks before mutation.

ALTER TABLE employees DISABLE ROW LEVEL SECURITY;
ALTER TABLE fields DISABLE ROW LEVEL SECURITY;
ALTER TABLE activity_assignees DISABLE ROW LEVEL SECURITY;

DO $validation$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.employees AS employee
         WHERE NOT employee.active
           AND (
                EXISTS (
                    SELECT 1 FROM public.fields AS field
                     WHERE field.tenant_id = employee.tenant_id
                       AND field.responsible_employee_id = employee.id
                       AND field.active
                ) OR EXISTS (
                    SELECT 1 FROM public.activity_assignees AS assignment
                     WHERE assignment.tenant_id = employee.tenant_id
                       AND assignment.employee_id = employee.id
                       AND assignment.revoked_at IS NULL
                )
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Cannot install employee lifecycle guards: inactive employee has live responsibilities',
            CONSTRAINT = 'inactive_employee_has_live_responsibilities';
    END IF;
END
$validation$;

ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE fields ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_assignees ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION agriinsight_security.lock_active_employee_for_field_responsibility()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF NOT NEW.active OR NEW.responsible_employee_id IS NULL THEN
        RETURN NEW;
    END IF;

    PERFORM 1
      FROM public.employees AS employee
     WHERE employee.tenant_id = NEW.tenant_id
       AND employee.id = NEW.responsible_employee_id
       AND employee.active
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Active field responsibility requires an active employee',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'active_field_requires_active_responsible_employee';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_employee_for_field_responsibility() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.lock_active_employee_for_activity_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF NEW.revoked_at IS NOT NULL THEN
        RETURN NEW;
    END IF;

    PERFORM 1
      FROM public.employees AS employee
     WHERE employee.tenant_id = NEW.tenant_id
       AND employee.id = NEW.employee_id
       AND employee.active
     FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Active activity assignment requires an active employee',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'active_activity_assignment_requires_active_employee';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.lock_active_employee_for_activity_assignment() FROM PUBLIC;

CREATE FUNCTION agriinsight_security.reject_employee_deactivation_with_live_responsibilities()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF OLD.active AND NOT NEW.active AND (
        EXISTS (
            SELECT 1 FROM public.fields AS field
             WHERE field.tenant_id = OLD.tenant_id
               AND field.responsible_employee_id = OLD.id
               AND field.active
        ) OR EXISTS (
            SELECT 1 FROM public.activity_assignees AS assignment
             WHERE assignment.tenant_id = OLD.tenant_id
               AND assignment.employee_id = OLD.id
               AND assignment.revoked_at IS NULL
        )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Employee deactivation requires all live responsibilities to be cleared',
            TABLE = TG_TABLE_NAME,
            CONSTRAINT = 'employee_deactivation_requires_cleared_responsibilities';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.reject_employee_deactivation_with_live_responsibilities() FROM PUBLIC;

CREATE TRIGGER fields_active_responsible_employee_guard
    BEFORE INSERT OR UPDATE OF tenant_id, responsible_employee_id, active ON fields
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_employee_for_field_responsibility();

CREATE TRIGGER activity_assignees_active_employee_guard
    BEFORE INSERT OR UPDATE OF tenant_id, employee_id, revoked_at ON activity_assignees
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.lock_active_employee_for_activity_assignment();

CREATE TRIGGER employees_lifecycle_dependency_guard
    BEFORE UPDATE OF active ON employees
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.reject_employee_deactivation_with_live_responsibilities();
