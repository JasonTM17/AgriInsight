\set ON_ERROR_STOP on

-- Required psql variables:
-- tenant_code, tenant_display_name, admin_display_name, admin_email, issuer, subject, correlation_id.
-- Run only as the verified migration owner after migrations complete.

BEGIN;

SELECT pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('agriinsight:tenant:' || upper(btrim(:'tenant_code')), 0)
);
SELECT pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('agriinsight:identity:' || :'issuer' || ':' || :'subject', 0)
);

SELECT pg_catalog.gen_random_uuid() AS new_tenant_id \gset
SELECT pg_catalog.gen_random_uuid() AS new_profile_id \gset
SELECT pg_catalog.set_config('app.tenant_id', :'new_tenant_id', TRUE);

INSERT INTO public.tenants (id, code, display_name)
VALUES (
    :'new_tenant_id'::UUID,
    upper(btrim(:'tenant_code')),
    :'tenant_display_name'
);

INSERT INTO public.user_profiles (id, tenant_id, display_name, email)
VALUES (
    :'new_profile_id'::UUID,
    :'new_tenant_id'::UUID,
    :'admin_display_name',
    NULLIF(:'admin_email', '')
);

INSERT INTO public.external_identities (
    id,
    tenant_id,
    user_profile_id,
    issuer,
    subject
)
VALUES (
    pg_catalog.gen_random_uuid(),
    :'new_tenant_id'::UUID,
    :'new_profile_id'::UUID,
    :'issuer',
    :'subject'
);

INSERT INTO public.user_roles (
    id,
    tenant_id,
    user_profile_id,
    role_code
)
VALUES (
    pg_catalog.gen_random_uuid(),
    :'new_tenant_id'::UUID,
    :'new_profile_id'::UUID,
    'TENANT_ADMIN'
);

INSERT INTO public.tenant_audit_events (
    id,
    tenant_id,
    actor_type,
    actor_reference,
    action,
    target_type,
    target_id,
    target_reference,
    correlation_id,
    outcome
)
VALUES (
    pg_catalog.gen_random_uuid(),
    :'new_tenant_id'::UUID,
    'OPERATOR',
    current_user,
    'FIRST_TENANT_ADMIN_PROVISIONED',
    'USER_PROFILE',
    :'new_profile_id'::UUID,
    upper(btrim(:'tenant_code')),
    NULLIF(:'correlation_id', ''),
    'SUCCEEDED'
);

COMMIT;

SELECT
    :'new_tenant_id'::UUID AS tenant_id,
    :'new_profile_id'::UUID AS profile_id;
