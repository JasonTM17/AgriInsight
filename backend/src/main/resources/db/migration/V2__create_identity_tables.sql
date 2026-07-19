CREATE SCHEMA agriinsight_security;

REVOKE ALL ON SCHEMA agriinsight_security FROM PUBLIC;

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    employee_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_profiles_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_user_profiles_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT user_profiles_display_name_nonblank CHECK (
        btrim(
            display_name,
            U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
        ) <> ''
    ),
    CONSTRAINT user_profiles_email_nonempty CHECK (email IS NULL OR email <> ''),
    CONSTRAINT user_profiles_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX ix_user_profiles_tenant_active ON user_profiles (tenant_id, active);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    issuer VARCHAR(2048) COLLATE "C" NOT NULL,
    subject VARCHAR(512) COLLATE "C" NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_external_identities_profile
        FOREIGN KEY (tenant_id, user_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT external_identities_issuer_nonempty CHECK (issuer <> ''),
    CONSTRAINT external_identities_subject_nonempty CHECK (subject <> ''),
    CONSTRAINT external_identities_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ux_external_identities_issuer_subject UNIQUE (issuer, subject)
);

CREATE INDEX ix_external_identities_profile
    ON external_identities (tenant_id, user_profile_id, active);

CREATE TABLE roles (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    CONSTRAINT roles_code_grammar CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT roles_display_name_nonblank CHECK (btrim(display_name) <> '')
);

CREATE TABLE permissions (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    CONSTRAINT permissions_code_grammar CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT permissions_display_name_nonblank CHECK (btrim(display_name) <> '')
);

CREATE TABLE user_roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_roles_profile
        FOREIGN KEY (tenant_id, user_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_code) REFERENCES roles (code),
    CONSTRAINT ux_user_roles_profile_role UNIQUE (tenant_id, user_profile_id, role_code),
    CONSTRAINT user_roles_version_nonnegative CHECK (version >= 0),
    CONSTRAINT user_roles_revocation_order CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX ix_user_roles_active_profile
    ON user_roles (tenant_id, user_profile_id, role_code)
    WHERE revoked_at IS NULL;

CREATE TABLE role_permissions (
    role_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_code) REFERENCES roles (code),
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_code) REFERENCES permissions (code)
);

CREATE FUNCTION agriinsight_security.resolve_identity_bootstrap(
    p_issuer TEXT,
    p_subject TEXT
)
RETURNS TABLE (
    profile_id UUID,
    tenant_id UUID,
    profile_active BOOLEAN,
    tenant_active BOOLEAN
)
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
    SELECT
        identity_row.user_profile_id,
        identity_row.tenant_id,
        profile.active,
        tenant.active
    FROM public.external_identities AS identity_row
    JOIN public.user_profiles AS profile
      ON profile.tenant_id = identity_row.tenant_id
     AND profile.id = identity_row.user_profile_id
    JOIN public.tenants AS tenant
      ON tenant.id = identity_row.tenant_id
    WHERE identity_row.issuer = p_issuer
      AND identity_row.subject = p_subject
      AND identity_row.active = TRUE
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT) FROM PUBLIC;
