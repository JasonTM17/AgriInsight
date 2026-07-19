CREATE FUNCTION agriinsight_security.app_current_tenant_id()
RETURNS UUID
LANGUAGE plpgsql
STABLE
PARALLEL SAFE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    configured_tenant TEXT;
BEGIN
    configured_tenant := pg_catalog.current_setting('app.tenant_id', TRUE);
    IF configured_tenant IS NULL
       OR configured_tenant = ''
       OR configured_tenant !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN NULL;
    END IF;

    RETURN configured_tenant::UUID;
EXCEPTION
    WHEN invalid_text_representation THEN
        RETURN NULL;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.app_current_tenant_id() FROM PUBLIC;

CREATE TABLE api_command_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    principal_id UUID NOT NULL,
    http_method VARCHAR(8) NOT NULL,
    route_template VARCHAR(240) NOT NULL,
    idempotency_key_digest CHAR(64) COLLATE "C" NOT NULL,
    canonical_schema_version SMALLINT NOT NULL,
    command_hash CHAR(64) COLLATE "C" NOT NULL,
    state VARCHAR(16) NOT NULL,
    response_status SMALLINT,
    target_resource_type VARCHAR(64),
    target_resource_id UUID,
    target_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_command_records_principal
        FOREIGN KEY (tenant_id, principal_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT ux_api_command_records_external_binding UNIQUE (
        tenant_id,
        principal_id,
        http_method,
        route_template,
        idempotency_key_digest
    ),
    CONSTRAINT api_command_records_method_grammar
        CHECK (http_method ~ '^[A-Z]{3,8}$'),
    CONSTRAINT api_command_records_route_template
        CHECK (route_template ~ '^/api/v1/[A-Za-z0-9_{}./-]+$'),
    CONSTRAINT api_command_records_key_digest
        CHECK (idempotency_key_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT api_command_records_schema_version
        CHECK (canonical_schema_version > 0),
    CONSTRAINT api_command_records_command_hash
        CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT api_command_records_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT api_command_records_completion_shape CHECK (
        (state = 'IN_PROGRESS'
            AND response_status IS NULL
            AND target_resource_type IS NULL
            AND target_resource_id IS NULL
            AND target_version IS NULL)
        OR
        (state = 'COMPLETED'
            AND response_status BETWEEN 200 AND 299
            AND target_resource_type IS NOT NULL
            AND target_resource_id IS NOT NULL
            AND target_version IS NOT NULL
            AND target_version >= 0)
    )
);

CREATE INDEX ix_api_command_records_tenant_created
    ON api_command_records (tenant_id, created_at DESC);

CREATE TABLE tenant_audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_profile_id UUID,
    actor_type VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(160),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID,
    target_reference VARCHAR(200),
    reason_code VARCHAR(80),
    correlation_id VARCHAR(128),
    outcome VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_audit_events_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenant_audit_events_actor
        FOREIGN KEY (tenant_id, actor_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT tenant_audit_events_actor_type
        CHECK (actor_type IN ('TENANT_USER', 'OPERATOR')),
    CONSTRAINT tenant_audit_events_actor_reference
        CHECK (
            (actor_type = 'TENANT_USER' AND actor_profile_id IS NOT NULL)
            OR
            (actor_type = 'OPERATOR' AND actor_profile_id IS NULL AND actor_reference IS NOT NULL)
        ),
    CONSTRAINT tenant_audit_events_action_nonblank CHECK (btrim(action) <> ''),
    CONSTRAINT tenant_audit_events_target_type_nonblank CHECK (btrim(target_type) <> ''),
    CONSTRAINT tenant_audit_events_outcome
        CHECK (outcome IN ('SUCCEEDED', 'DENIED', 'CONFLICT'))
);

CREATE INDEX ix_tenant_audit_events_tenant_occurred
    ON tenant_audit_events (tenant_id, occurred_at DESC);

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE external_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE external_identities FORCE ROW LEVEL SECURITY;
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE api_command_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_command_records FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_audit_events FORCE ROW LEVEL SECURITY;

CREATE POLICY runtime_tenant_isolation ON tenants
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_runtime
    USING (id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (id = agriinsight_security.app_current_tenant_id());

CREATE POLICY migration_tenant_isolation ON tenants
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (id = agriinsight_security.app_current_tenant_id());

CREATE POLICY identity_bootstrap_read ON tenants
    AS PERMISSIVE
    FOR SELECT
    TO agriinsight_identity_definer
    USING (TRUE);

CREATE POLICY runtime_tenant_isolation ON user_profiles
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY migration_tenant_isolation ON user_profiles
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY identity_bootstrap_read ON user_profiles
    AS PERMISSIVE
    FOR SELECT
    TO agriinsight_identity_definer
    USING (TRUE);

CREATE POLICY migration_tenant_isolation ON external_identities
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY identity_bootstrap_read ON external_identities
    AS PERMISSIVE
    FOR SELECT
    TO agriinsight_identity_definer
    USING (TRUE);

CREATE POLICY runtime_tenant_isolation ON user_roles
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY migration_tenant_isolation ON user_roles
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON api_command_records
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY migration_tenant_isolation ON api_command_records
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY runtime_tenant_isolation ON tenant_audit_events
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_runtime
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

CREATE POLICY migration_tenant_isolation ON tenant_audit_events
    AS PERMISSIVE
    FOR ALL
    TO agriinsight_migrator
    USING (tenant_id = agriinsight_security.app_current_tenant_id())
    WITH CHECK (tenant_id = agriinsight_security.app_current_tenant_id());

GRANT USAGE, CREATE ON SCHEMA agriinsight_security TO agriinsight_identity_definer;

ALTER FUNCTION agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT)
    OWNER TO agriinsight_identity_definer;
