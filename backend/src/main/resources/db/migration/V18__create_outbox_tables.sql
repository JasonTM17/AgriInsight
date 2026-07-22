CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    command_id UUID NOT NULL,
    event_ordinal INTEGER NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leased_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    lease_owner VARCHAR(128),
    lease_token UUID,
    lease_generation BIGINT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outbox_events_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_outbox_events_command
        FOREIGN KEY (tenant_id, command_id)
        REFERENCES api_command_records (tenant_id, id),
    CONSTRAINT ux_outbox_events_command_ordinal
        UNIQUE (tenant_id, command_id, event_ordinal),
    CONSTRAINT ux_outbox_events_aggregate_version
        UNIQUE (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT outbox_events_ordinal_nonnegative
        CHECK (event_ordinal >= 0),
    CONSTRAINT outbox_events_aggregate_type_canonical
        CHECK (aggregate_type = upper(btrim(aggregate_type))
            AND btrim(aggregate_type) <> ''),
    CONSTRAINT outbox_events_event_type_nonblank
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT outbox_events_schema_version_positive
        CHECK (schema_version > 0),
    CONSTRAINT outbox_events_aggregate_version_nonnegative
        CHECK (aggregate_version >= 0),
    CONSTRAINT outbox_events_status_valid
        CHECK (status IN ('PENDING', 'LEASED', 'PUBLISHED', 'DEAD_LETTER')),
    CONSTRAINT outbox_events_attempts_valid
        CHECK (attempts >= 0 AND max_attempts BETWEEN 1 AND 100 AND attempts <= max_attempts),
    CONSTRAINT outbox_events_lease_shape
        CHECK ((status = 'LEASED' AND leased_until IS NOT NULL AND lease_owner IS NOT NULL
                AND lease_token IS NOT NULL)
            OR (status <> 'LEASED')),
    CONSTRAINT outbox_events_published_shape
        CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (status <> 'PUBLISHED')),
    CONSTRAINT outbox_events_dead_letter_shape
        CHECK ((status = 'DEAD_LETTER' AND dead_lettered_at IS NOT NULL)
            OR (status <> 'DEAD_LETTER'))
);

CREATE INDEX ix_outbox_events_claim
    ON outbox_events (status, available_at, occurred_at, id);
CREATE INDEX ix_outbox_events_aggregate_order
    ON outbox_events (tenant_id, aggregate_type, aggregate_id, aggregate_version);
CREATE INDEX ix_outbox_events_command
    ON outbox_events (tenant_id, command_id, event_ordinal);
