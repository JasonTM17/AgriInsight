CREATE SCHEMA IF NOT EXISTS web_auth_spike AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA web_auth_spike FROM PUBLIC;

CREATE TABLE IF NOT EXISTS web_auth_spike.preauth_requests (
    id uuid PRIMARY KEY,
    state_hash bytea NOT NULL UNIQUE CHECK (octet_length(state_hash) = 32),
    browser_binding_hash bytea NOT NULL CHECK (octet_length(browser_binding_hash) = 32),
    pkce_verifier_ciphertext bytea NOT NULL,
    nonce_ciphertext bytea NOT NULL,
    token_key_id text NOT NULL,
    return_path text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS preauth_requests_expiry_idx
    ON web_auth_spike.preauth_requests (expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS web_auth_spike.sessions (
    id uuid PRIMARY KEY,
    session_token_hash bytea NOT NULL UNIQUE CHECK (octet_length(session_token_hash) = 32),
    provider_issuer text NOT NULL,
    provider_subject text NOT NULL,
    token_key_id text NOT NULL,
    access_token_ciphertext bytea NOT NULL,
    refresh_token_ciphertext bytea,
    id_token_ciphertext bytea,
    access_token_expires_at timestamptz NOT NULL,
    session_expires_at timestamptz NOT NULL,
    session_version bigint NOT NULL DEFAULT 1 CHECK (session_version > 0),
    refresh_version bigint NOT NULL DEFAULT 0 CHECK (refresh_version >= 0),
    refresh_lease_id uuid,
    refresh_lease_version bigint,
    refresh_lease_expires_at timestamptz,
    refresh_retry_after timestamptz,
    refresh_attempted_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (refresh_lease_id IS NULL AND refresh_lease_version IS NULL AND refresh_lease_expires_at IS NULL)
        OR
        (refresh_lease_id IS NOT NULL AND refresh_lease_version IS NOT NULL AND refresh_lease_expires_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS sessions_expiry_idx
    ON web_auth_spike.sessions (session_expires_at)
    WHERE revoked_at IS NULL;

REVOKE ALL ON ALL TABLES IN SCHEMA web_auth_spike FROM PUBLIC;
