\set ON_ERROR_STOP on

-- Required psql variables: legacy_owner and migration_owner.
SELECT pg_catalog.set_config('agriinsight.adoption_legacy_owner', :'legacy_owner', FALSE);
SELECT pg_catalog.set_config('agriinsight.adoption_migration_owner', :'migration_owner', FALSE);

DO $adoption$
DECLARE
    legacy_owner NAME := pg_catalog.current_setting('agriinsight.adoption_legacy_owner')::NAME;
    migration_owner NAME := pg_catalog.current_setting('agriinsight.adoption_migration_owner')::NAME;
    expected_table NAME;
    object_owner NAME;
    unexpected_object TEXT;
BEGIN
    IF legacy_owner = migration_owner THEN
        RAISE EXCEPTION 'Legacy and migration owners must be different';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = legacy_owner)
       OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = migration_owner) THEN
        RAISE EXCEPTION 'Both adoption roles must already exist';
    END IF;

    IF current_user <> legacy_owner
       AND NOT pg_catalog.pg_has_role(current_user, legacy_owner, 'SET') THEN
        RAISE EXCEPTION 'Adoption connection cannot SET ROLE to legacy owner %', legacy_owner;
    END IF;

    IF current_user <> migration_owner
       AND NOT pg_catalog.pg_has_role(current_user, migration_owner, 'SET') THEN
        RAISE EXCEPTION 'Adoption connection cannot SET ROLE to migration owner %', migration_owner;
    END IF;

    SELECT schema_name INTO unexpected_object
    FROM information_schema.schemata
    WHERE schema_name NOT IN ('public', 'agriinsight_security', 'information_schema')
      AND schema_name NOT LIKE 'pg\_%' ESCAPE '\'
    ORDER BY schema_name
    LIMIT 1;
    IF unexpected_object IS NOT NULL THEN
        RAISE EXCEPTION 'Shared database refused; unexpected schema %', unexpected_object;
    END IF;

    SELECT pg_catalog.format('%I.%I', namespace.nspname, relation.relname)
      INTO unexpected_object
    FROM pg_catalog.pg_class AS relation
    JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname IN ('public', 'agriinsight_security')
      AND relation.relkind IN ('r', 'p', 'S', 'v', 'm', 'f')
      AND NOT (
          namespace.nspname = 'public'
          AND relation.relkind IN ('r', 'p')
          AND relation.relname IN (
              'tenants',
              'user_profiles',
              'external_identities',
              'roles',
              'permissions',
              'user_roles',
              'role_permissions',
              'flyway_schema_history'
          )
      )
    ORDER BY namespace.nspname, relation.relname
    LIMIT 1;
    IF unexpected_object IS NOT NULL THEN
        RAISE EXCEPTION 'Unexpected V1-V3 relation refused: %', unexpected_object;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_proc AS routine
        JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = routine.pronamespace
        WHERE namespace.nspname = 'agriinsight_security'
          AND NOT (
              routine.proname = 'resolve_identity_bootstrap'
              AND pg_catalog.pg_get_function_identity_arguments(routine.oid) = 'p_issuer text, p_subject text'
          )
    ) THEN
        RAISE EXCEPTION 'Unexpected V1-V3 security routine refused';
    END IF;

    FOREACH expected_table IN ARRAY ARRAY[
        'tenants'::NAME,
        'user_profiles'::NAME,
        'external_identities'::NAME,
        'roles'::NAME,
        'permissions'::NAME,
        'user_roles'::NAME,
        'role_permissions'::NAME,
        'flyway_schema_history'::NAME
    ]
    LOOP
        SELECT owner.rolname
          INTO object_owner
        FROM pg_catalog.pg_class AS relation
        JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = relation.relnamespace
        JOIN pg_catalog.pg_roles AS owner ON owner.oid = relation.relowner
        WHERE namespace.nspname = 'public'
          AND relation.relname = expected_table
          AND relation.relkind IN ('r', 'p');

        IF object_owner IS NULL THEN
            RAISE EXCEPTION 'Expected V1-V3 table public.% is missing', expected_table;
        END IF;
        IF object_owner <> legacy_owner THEN
            RAISE EXCEPTION 'Unexpected owner % for public.%; expected %', object_owner, expected_table, legacy_owner;
        END IF;
        RAISE NOTICE 'Adopting public.% from % to %', expected_table, legacy_owner, migration_owner;
        EXECUTE pg_catalog.format('ALTER TABLE public.%I OWNER TO %I', expected_table, migration_owner);
    END LOOP;

    SELECT owner.rolname
      INTO object_owner
    FROM pg_catalog.pg_namespace AS namespace
    JOIN pg_catalog.pg_roles AS owner ON owner.oid = namespace.nspowner
    WHERE namespace.nspname = 'agriinsight_security';
    IF object_owner IS DISTINCT FROM legacy_owner THEN
        RAISE EXCEPTION 'Unexpected owner % for agriinsight_security schema; expected %', object_owner, legacy_owner;
    END IF;

    SELECT owner.rolname
      INTO object_owner
    FROM pg_catalog.pg_proc AS routine
    JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = routine.pronamespace
    JOIN pg_catalog.pg_roles AS owner ON owner.oid = routine.proowner
    WHERE namespace.nspname = 'agriinsight_security'
      AND routine.proname = 'resolve_identity_bootstrap'
      AND pg_catalog.pg_get_function_identity_arguments(routine.oid) = 'p_issuer text, p_subject text';
    IF object_owner IS DISTINCT FROM legacy_owner THEN
        RAISE EXCEPTION 'Unexpected owner % for identity resolver; expected %', object_owner, legacy_owner;
    END IF;

    RAISE NOTICE 'Adopting agriinsight_security schema and identity resolver from % to %', legacy_owner, migration_owner;
    EXECUTE pg_catalog.format('ALTER SCHEMA agriinsight_security OWNER TO %I', migration_owner);
    EXECUTE pg_catalog.format(
        'ALTER FUNCTION agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT) OWNER TO %I',
        migration_owner
    );

    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_class AS relation
        JOIN pg_catalog.pg_namespace AS namespace ON namespace.oid = relation.relnamespace
        JOIN pg_catalog.pg_roles AS owner ON owner.oid = relation.relowner
        WHERE namespace.nspname = 'public'
          AND relation.relname IN (
              'tenants',
              'user_profiles',
              'external_identities',
              'roles',
              'permissions',
              'user_roles',
              'role_permissions',
              'flyway_schema_history'
          )
          AND owner.rolname <> migration_owner
    ) THEN
        RAISE EXCEPTION 'Ownership adoption verification failed';
    END IF;
END
$adoption$;

RESET agriinsight.adoption_legacy_owner;
RESET agriinsight.adoption_migration_owner;
