CREATE TABLE cost_categories (
    code VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cost_categories_code_canonical
        CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT cost_categories_display_name_nonblank
        CHECK (btrim(display_name) <> '')
);

INSERT INTO cost_categories (code, display_name)
VALUES
    ('LABOR', 'Labor'),
    ('MATERIAL', 'Material'),
    ('MACHINERY', 'Machinery'),
    ('TRANSPORT', 'Transport'),
    ('UTILITY', 'Utility'),
    ('OTHER', 'Other');

CREATE TABLE operating_cost_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    farm_id UUID,
    field_id UUID,
    season_id UUID,
    activity_id UUID,
    category_code VARCHAR(32) NOT NULL,
    amount_vnd NUMERIC(19, 2) NOT NULL,
    entry_kind VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    description VARCHAR(1000),
    source_reference VARCHAR(200),
    reversal_of UUID,
    command_reference CHAR(64) NOT NULL,
    recorded_by_profile_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_operating_cost_entries_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_operating_cost_entries_farm
        FOREIGN KEY (tenant_id, farm_id) REFERENCES farms (tenant_id, id),
    CONSTRAINT fk_operating_cost_entries_field
        FOREIGN KEY (tenant_id, field_id) REFERENCES fields (tenant_id, id),
    CONSTRAINT fk_operating_cost_entries_season
        FOREIGN KEY (tenant_id, season_id) REFERENCES seasons (tenant_id, id),
    CONSTRAINT fk_operating_cost_entries_activity
        FOREIGN KEY (tenant_id, activity_id) REFERENCES activities (tenant_id, id),
    CONSTRAINT fk_operating_cost_entries_category
        FOREIGN KEY (category_code) REFERENCES cost_categories (code),
    CONSTRAINT fk_operating_cost_entries_profile
        FOREIGN KEY (tenant_id, recorded_by_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT fk_operating_cost_entries_reversal
        FOREIGN KEY (tenant_id, reversal_of)
        REFERENCES operating_cost_entries (tenant_id, id),
    CONSTRAINT ux_operating_cost_entries_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_operating_cost_entries_reversal
        UNIQUE (tenant_id, reversal_of),
    CONSTRAINT ux_operating_cost_entries_command_kind
        UNIQUE (tenant_id, command_reference, entry_kind),
    CONSTRAINT operating_cost_entries_target_type
        CHECK (target_type IN ('TENANT', 'FARM', 'FIELD', 'SEASON', 'ACTIVITY')),
    CONSTRAINT operating_cost_entries_target_shape CHECK (
        (target_type = 'TENANT'
            AND farm_id IS NULL AND field_id IS NULL
            AND season_id IS NULL AND activity_id IS NULL)
        OR (target_type = 'FARM'
            AND farm_id IS NOT NULL AND field_id IS NULL
            AND season_id IS NULL AND activity_id IS NULL)
        OR (target_type = 'FIELD'
            AND farm_id IS NULL AND field_id IS NOT NULL
            AND season_id IS NULL AND activity_id IS NULL)
        OR (target_type = 'SEASON'
            AND farm_id IS NULL AND field_id IS NULL
            AND season_id IS NOT NULL AND activity_id IS NULL)
        OR (target_type = 'ACTIVITY'
            AND farm_id IS NULL AND field_id IS NULL
            AND season_id IS NULL AND activity_id IS NOT NULL)
    ),
    CONSTRAINT operating_cost_entries_amount_positive CHECK (amount_vnd > 0),
    CONSTRAINT operating_cost_entries_kind
        CHECK (entry_kind IN ('POSTING', 'REVERSAL')),
    CONSTRAINT operating_cost_entries_kind_shape CHECK (
        (entry_kind = 'POSTING' AND reversal_of IS NULL)
        OR (entry_kind = 'REVERSAL' AND reversal_of IS NOT NULL)
    ),
    CONSTRAINT operating_cost_entries_description_nonblank
        CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT operating_cost_entries_source_nonblank
        CHECK (source_reference IS NULL OR btrim(source_reference) <> ''),
    CONSTRAINT operating_cost_entries_command_reference
        CHECK (command_reference ~ '^[0-9a-f]{64}$'),
    CONSTRAINT operating_cost_entries_not_self_reversal
        CHECK (reversal_of IS NULL OR reversal_of <> id),
    CONSTRAINT operating_cost_entries_version_zero CHECK (version = 0)
);

CREATE INDEX ix_operating_cost_entries_tenant_occurred
    ON operating_cost_entries (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX ix_operating_cost_entries_tenant_category_occurred
    ON operating_cost_entries (tenant_id, category_code, occurred_at DESC, id DESC);
CREATE INDEX ix_operating_cost_entries_tenant_farm_occurred
    ON operating_cost_entries (tenant_id, farm_id, occurred_at DESC, id DESC)
    WHERE farm_id IS NOT NULL;
CREATE INDEX ix_operating_cost_entries_tenant_field_occurred
    ON operating_cost_entries (tenant_id, field_id, occurred_at DESC, id DESC)
    WHERE field_id IS NOT NULL;
CREATE INDEX ix_operating_cost_entries_tenant_season_occurred
    ON operating_cost_entries (tenant_id, season_id, occurred_at DESC, id DESC)
    WHERE season_id IS NOT NULL;
CREATE INDEX ix_operating_cost_entries_tenant_activity_occurred
    ON operating_cost_entries (tenant_id, activity_id, occurred_at DESC, id DESC)
    WHERE activity_id IS NOT NULL;

CREATE FUNCTION agriinsight_security.validate_operating_cost_reversal()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public
AS $function$
DECLARE
    original public.operating_cost_entries%ROWTYPE;
BEGIN
    IF NEW.entry_kind = 'POSTING' THEN
        RETURN NEW;
    END IF;

    SELECT *
      INTO original
      FROM public.operating_cost_entries AS entry
     WHERE entry.tenant_id = NEW.tenant_id
       AND entry.id = NEW.reversal_of
     FOR KEY SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Operating cost reversal target is unavailable'
            USING ERRCODE = '23503';
    END IF;
    IF original.entry_kind <> 'POSTING' THEN
        RAISE EXCEPTION 'An operating cost reversal must target a posting'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.target_type <> original.target_type
       OR NEW.farm_id IS DISTINCT FROM original.farm_id
       OR NEW.field_id IS DISTINCT FROM original.field_id
       OR NEW.season_id IS DISTINCT FROM original.season_id
       OR NEW.activity_id IS DISTINCT FROM original.activity_id
       OR NEW.category_code <> original.category_code
       OR NEW.amount_vnd <> original.amount_vnd THEN
        RAISE EXCEPTION 'Operating cost reversal must copy the original financial dimensions'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.validate_operating_cost_reversal()
    FROM PUBLIC;

CREATE TRIGGER validate_operating_cost_reversal
BEFORE INSERT ON operating_cost_entries
FOR EACH ROW
EXECUTE FUNCTION agriinsight_security.validate_operating_cost_reversal();
