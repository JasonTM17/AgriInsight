-- Run as a narrowly held PostgreSQL operator with permission to create roles.
-- Passwords are intentionally absent. Provision login secrets through the deployment secret store.

DO $bootstrap$
DECLARE
    role_row RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'agriinsight_migrator') THEN
        CREATE ROLE agriinsight_migrator
            LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'agriinsight_runtime') THEN
        CREATE ROLE agriinsight_runtime
            LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'agriinsight_identity_definer') THEN
        CREATE ROLE agriinsight_identity_definer
            NOLOGIN NOSUPERUSER NOINHERIT NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;

    FOR role_row IN
        SELECT *
        FROM (VALUES
            ('agriinsight_migrator'::NAME, TRUE, TRUE),
            ('agriinsight_runtime'::NAME, TRUE, TRUE),
            ('agriinsight_identity_definer'::NAME, FALSE, FALSE)
        ) AS expected(role_name, can_login, inherits)
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_catalog.pg_roles AS actual
            WHERE actual.rolname = role_row.role_name
              AND actual.rolcanlogin = role_row.can_login
              AND actual.rolinherit = role_row.inherits
              AND actual.rolsuper = FALSE
              AND actual.rolcreatedb = FALSE
              AND actual.rolcreaterole = FALSE
              AND actual.rolreplication = FALSE
              AND actual.rolbypassrls = FALSE
        ) THEN
            RAISE EXCEPTION 'Role % exists with unsafe or unexpected attributes', role_row.role_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members AS membership
        JOIN pg_catalog.pg_roles AS granted_role ON granted_role.oid = membership.roleid
        JOIN pg_catalog.pg_roles AS member_role ON member_role.oid = membership.member
        WHERE member_role.rolname IN ('agriinsight_runtime', 'agriinsight_identity_definer')
           OR granted_role.rolname IN ('agriinsight_runtime', 'agriinsight_migrator')
           OR (
               member_role.rolname = 'agriinsight_migrator'
               AND granted_role.rolname <> 'agriinsight_identity_definer'
           )
           OR (
               granted_role.rolname = 'agriinsight_identity_definer'
               AND member_role.rolname <> 'agriinsight_migrator'
           )
    ) THEN
        RAISE EXCEPTION 'AgriInsight roles have a forbidden role membership';
    END IF;
END
$bootstrap$;

GRANT agriinsight_identity_definer TO agriinsight_migrator
    WITH INHERIT FALSE, SET TRUE;

DO $membership_gate$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members AS membership
        JOIN pg_catalog.pg_roles AS granted_role ON granted_role.oid = membership.roleid
        JOIN pg_catalog.pg_roles AS member_role ON member_role.oid = membership.member
        WHERE granted_role.rolname = 'agriinsight_identity_definer'
          AND member_role.rolname = 'agriinsight_migrator'
          AND membership.admin_option = FALSE
          AND membership.inherit_option = FALSE
          AND membership.set_option = TRUE
    ) THEN
        RAISE EXCEPTION 'Migration role cannot safely SET ROLE to the identity definer';
    END IF;
END
$membership_gate$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO agriinsight_migrator;
GRANT USAGE ON SCHEMA public TO agriinsight_runtime;
GRANT USAGE ON SCHEMA public TO agriinsight_identity_definer;

DO $database_access$
BEGIN
    EXECUTE pg_catalog.format(
        'GRANT CONNECT ON DATABASE %I TO agriinsight_migrator, agriinsight_runtime',
        pg_catalog.current_database()
    );
    EXECUTE pg_catalog.format(
        'GRANT CREATE ON DATABASE %I TO agriinsight_migrator',
        pg_catalog.current_database()
    );
END
$database_access$;
