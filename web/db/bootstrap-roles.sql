\set ON_ERROR_STOP on

\if :{?web_migrator_password}
\else
  \echo 'web_migrator_password variable is required'
  \quit 3
\endif
\if :{?web_runtime_password}
\else
  \echo 'web_runtime_password variable is required'
  \quit 3
\endif

SELECT format(
  'CREATE ROLE agriinsight_web_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_owner') \gexec

SELECT format(
  'CREATE ROLE agriinsight_web_migrator LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS',
  :'web_migrator_password'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_migrator') \gexec

SELECT format(
  'CREATE ROLE agriinsight_web_runtime LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS',
  :'web_runtime_password'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_runtime') \gexec

ALTER ROLE agriinsight_web_migrator PASSWORD :'web_migrator_password';
ALTER ROLE agriinsight_web_runtime PASSWORD :'web_runtime_password';

DO $role_gate$
DECLARE
    role_row RECORD;
BEGIN
    FOR role_row IN
        SELECT *
        FROM (VALUES
            ('agriinsight_web_owner'::NAME, FALSE),
            ('agriinsight_web_migrator'::NAME, TRUE),
            ('agriinsight_web_runtime'::NAME, TRUE)
        ) AS expected(role_name, can_login)
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_catalog.pg_roles AS actual
            WHERE actual.rolname = role_row.role_name
              AND actual.rolcanlogin = role_row.can_login
              AND actual.rolinherit = FALSE
              AND actual.rolsuper = FALSE
              AND actual.rolcreatedb = FALSE
              AND actual.rolcreaterole = FALSE
              AND actual.rolreplication = FALSE
              AND actual.rolbypassrls = FALSE
        ) THEN
            RAISE EXCEPTION 'Role % exists with unsafe or unexpected attributes',
                role_row.role_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members AS membership
        JOIN pg_catalog.pg_roles AS granted_role
          ON granted_role.oid = membership.roleid
        JOIN pg_catalog.pg_roles AS member_role
          ON member_role.oid = membership.member
        WHERE member_role.rolname IN (
                  'agriinsight_web_owner',
                  'agriinsight_web_runtime'
              )
           OR granted_role.rolname IN (
                  'agriinsight_web_migrator',
                  'agriinsight_web_runtime'
              )
           OR (
               member_role.rolname = 'agriinsight_web_migrator'
               AND granted_role.rolname <> 'agriinsight_web_owner'
           )
           OR (
               granted_role.rolname = 'agriinsight_web_owner'
               AND member_role.rolname <> 'agriinsight_web_migrator'
           )
    ) THEN
        RAISE EXCEPTION 'AgriInsight web roles have a forbidden role membership';
    END IF;
END
$role_gate$;

GRANT agriinsight_web_owner TO agriinsight_web_migrator
    WITH INHERIT FALSE, SET TRUE;
REVOKE agriinsight_web_owner FROM agriinsight_web_runtime;

DO $membership_gate$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members AS membership
        JOIN pg_catalog.pg_roles AS granted_role
          ON granted_role.oid = membership.roleid
        JOIN pg_catalog.pg_roles AS member_role
          ON member_role.oid = membership.member
        WHERE granted_role.rolname = 'agriinsight_web_owner'
          AND member_role.rolname = 'agriinsight_web_migrator'
          AND membership.admin_option = FALSE
          AND membership.inherit_option = FALSE
          AND membership.set_option = TRUE
    ) THEN
        RAISE EXCEPTION 'Web migrator cannot safely SET ROLE to web owner';
    END IF;
END
$membership_gate$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM agriinsight_web_runtime;
SELECT format('GRANT CREATE ON DATABASE %I TO agriinsight_web_owner', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO agriinsight_web_migrator', current_database()) \gexec
SELECT format('REVOKE CREATE ON DATABASE %I FROM agriinsight_web_migrator', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO agriinsight_web_runtime', current_database()) \gexec
SELECT format('REVOKE CREATE ON DATABASE %I FROM agriinsight_web_runtime', current_database()) \gexec
