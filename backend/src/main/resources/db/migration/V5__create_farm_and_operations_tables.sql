-- Farm and operations schema is tenant-owned. RLS policies are installed by V6.

CREATE TABLE farms (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_farms_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_farms_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_farms_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT farms_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT farms_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT farms_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT farms_version_nonnegative CHECK (version >= 0),
    CONSTRAINT farms_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_farms_tenant_active_code ON farms (tenant_id, active, code);

CREATE TABLE crops (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    scientific_name VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_crops_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_crops_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_crops_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT crops_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT crops_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT crops_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT crops_scientific_name_nonblank CHECK (scientific_name IS NULL OR btrim(scientific_name) <> ''),
    CONSTRAINT crops_version_nonnegative CHECK (version >= 0),
    CONSTRAINT crops_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_crops_tenant_active_code ON crops (tenant_id, active, code);

CREATE TABLE employees (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    job_title VARCHAR(160),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employees_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ux_employees_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_employees_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT employees_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT employees_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT employees_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT employees_job_title_nonblank CHECK (job_title IS NULL OR btrim(job_title) <> ''),
    CONSTRAINT employees_version_nonnegative CHECK (version >= 0),
    CONSTRAINT employees_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_employees_tenant_active_code ON employees (tenant_id, active, code);

ALTER TABLE user_profiles
    ADD CONSTRAINT fk_user_profiles_employee
        FOREIGN KEY (tenant_id, employee_id) REFERENCES employees (tenant_id, id),
    ADD CONSTRAINT ux_user_profiles_tenant_employee UNIQUE (tenant_id, employee_id);

CREATE TABLE fields (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    area_hectares NUMERIC(14, 4) NOT NULL,
    responsible_employee_id UUID,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    soil_type VARCHAR(120),
    irrigation_type VARCHAR(120),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fields_farm FOREIGN KEY (tenant_id, farm_id)
        REFERENCES farms (tenant_id, id),
    CONSTRAINT fk_fields_responsible_employee FOREIGN KEY (tenant_id, responsible_employee_id)
        REFERENCES employees (tenant_id, id),
    CONSTRAINT ux_fields_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_fields_tenant_farm_id UNIQUE (tenant_id, farm_id, id),
    CONSTRAINT ux_fields_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fields_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT fields_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT fields_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT fields_area_positive CHECK (area_hectares > 0),
    CONSTRAINT fields_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT fields_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT fields_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT fields_soil_type_nonblank CHECK (soil_type IS NULL OR btrim(soil_type) <> ''),
    CONSTRAINT fields_irrigation_nonblank CHECK (irrigation_type IS NULL OR btrim(irrigation_type) <> ''),
    CONSTRAINT fields_version_nonnegative CHECK (version >= 0),
    CONSTRAINT fields_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_fields_tenant_farm_active_code
    ON fields (tenant_id, farm_id, active, code);
CREATE INDEX ix_fields_tenant_responsible_employee
    ON fields (tenant_id, responsible_employee_id)
    WHERE responsible_employee_id IS NOT NULL;

CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    field_id UUID NOT NULL,
    crop_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    variety_name VARCHAR(160),
    planned_start_date DATE NOT NULL,
    planned_end_date DATE NOT NULL,
    started_on DATE,
    ended_on DATE,
    planted_area_hectares NUMERIC(14, 4) NOT NULL,
    budget_vnd NUMERIC(19, 2),
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seasons_field FOREIGN KEY (tenant_id, farm_id, field_id)
        REFERENCES fields (tenant_id, farm_id, id),
    CONSTRAINT fk_seasons_crop FOREIGN KEY (tenant_id, crop_id)
        REFERENCES crops (tenant_id, id),
    CONSTRAINT ux_seasons_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_seasons_tenant_farm_field_id UNIQUE (tenant_id, farm_id, field_id, id),
    CONSTRAINT ux_seasons_tenant_hierarchy_id UNIQUE (tenant_id, farm_id, field_id, crop_id, id),
    CONSTRAINT ux_seasons_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT seasons_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT seasons_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT seasons_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT seasons_variety_nonblank CHECK (variety_name IS NULL OR btrim(variety_name) <> ''),
    CONSTRAINT seasons_planned_date_order CHECK (planned_end_date >= planned_start_date),
    CONSTRAINT seasons_actual_date_order CHECK (ended_on IS NULL OR started_on IS NULL OR ended_on >= started_on),
    CONSTRAINT seasons_area_positive CHECK (planted_area_hectares > 0),
    CONSTRAINT seasons_budget_nonnegative CHECK (budget_vnd IS NULL OR budget_vnd >= 0),
    CONSTRAINT seasons_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT seasons_status_dates CHECK (
        (status = 'PLANNED' AND started_on IS NULL AND ended_on IS NULL)
        OR (status = 'ACTIVE' AND started_on IS NOT NULL AND ended_on IS NULL)
        OR (status = 'COMPLETED' AND started_on IS NOT NULL AND ended_on IS NOT NULL)
        OR (status = 'CANCELLED' AND ended_on IS NOT NULL)
    ),
    CONSTRAINT seasons_version_nonnegative CHECK (version >= 0),
    CONSTRAINT seasons_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_seasons_tenant_farm_status_dates
    ON seasons (tenant_id, farm_id, status, planned_start_date, id);
CREATE INDEX ix_seasons_tenant_field_dates
    ON seasons (tenant_id, field_id, planned_start_date, id);
CREATE INDEX ix_seasons_tenant_crop_dates
    ON seasons (tenant_id, crop_id, planned_start_date, id);

CREATE TABLE user_farm_assignments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_farm_assignment_profile FOREIGN KEY (tenant_id, user_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT fk_user_farm_assignment_farm FOREIGN KEY (tenant_id, farm_id)
        REFERENCES farms (tenant_id, id),
    CONSTRAINT ux_user_farm_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT user_farm_assignment_version_nonnegative CHECK (version >= 0),
    CONSTRAINT user_farm_assignment_revocation_order CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT user_farm_assignment_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX ux_user_farm_assignment_active
    ON user_farm_assignments (tenant_id, user_profile_id, farm_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_user_farm_assignment_farm_active
    ON user_farm_assignments (tenant_id, farm_id, user_profile_id)
    WHERE revoked_at IS NULL;

CREATE TABLE activity_types (
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, code),
    CONSTRAINT fk_activity_types_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT activity_types_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT activity_types_code CHECK (code IN (
        'PLANTING', 'IRRIGATION', 'FERTILIZATION', 'PEST_CONTROL',
        'WEEDING', 'PEST_INSPECTION', 'HARVEST', 'TRANSPORT')),
    CONSTRAINT activity_types_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT activity_types_version_nonnegative CHECK (version >= 0),
    CONSTRAINT activity_types_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_activity_types_tenant_active_code
    ON activity_types (tenant_id, active, code);

-- V4 forces RLS on tenants, so the migration owner cannot enumerate existing tenants
-- without explicitly dropping FORCE. RLS remains enabled for non-owner runtime sessions,
-- and PostgreSQL rolls both ALTER statements back if this transactional migration fails.
ALTER TABLE tenants NO FORCE ROW LEVEL SECURITY;

INSERT INTO activity_types (tenant_id, code, display_name)
SELECT tenant.id, default_type.code, default_type.display_name
FROM tenants AS tenant
CROSS JOIN (VALUES
    ('PLANTING', 'Planting'),
    ('IRRIGATION', 'Irrigation'),
    ('FERTILIZATION', 'Fertilization'),
    ('PEST_CONTROL', 'Pest control'),
    ('WEEDING', 'Weeding'),
    ('PEST_INSPECTION', 'Pest inspection'),
    ('HARVEST', 'Harvest'),
    ('TRANSPORT', 'Transport')
) AS default_type(code, display_name);

ALTER TABLE tenants FORCE ROW LEVEL SECURITY;

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    field_id UUID NOT NULL,
    season_id UUID NOT NULL,
    activity_type_code VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    planned_start_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activities_season FOREIGN KEY (tenant_id, farm_id, field_id, season_id)
        REFERENCES seasons (tenant_id, farm_id, field_id, id),
    CONSTRAINT fk_activities_type FOREIGN KEY (tenant_id, activity_type_code)
        REFERENCES activity_types (tenant_id, code),
    CONSTRAINT ux_activities_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_activities_tenant_farm_id UNIQUE (tenant_id, farm_id, id),
    CONSTRAINT ux_activities_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT activities_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT activities_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT activities_title_nonblank CHECK (btrim(title) <> ''),
    CONSTRAINT activities_description_nonblank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT activities_planned_time_order CHECK (due_at >= planned_start_at),
    CONSTRAINT activities_status CHECK (status IN ('PLANNED', 'STARTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT activities_status_times CHECK (
        (status = 'PLANNED' AND started_at IS NULL AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'STARTED' AND started_at IS NOT NULL AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT activities_completion_order CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT activities_cancellation_order CHECK (
        cancelled_at IS NULL OR started_at IS NULL OR cancelled_at >= started_at
    ),
    CONSTRAINT activities_version_nonnegative CHECK (version >= 0),
    CONSTRAINT activities_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_activities_tenant_farm_status_due
    ON activities (tenant_id, farm_id, status, due_at, id);
CREATE INDEX ix_activities_tenant_field_status_due
    ON activities (tenant_id, field_id, status, due_at, id);
CREATE INDEX ix_activities_tenant_season_status_due
    ON activities (tenant_id, season_id, status, due_at, id);

CREATE TABLE activity_assignees (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_assignees_activity FOREIGN KEY (tenant_id, activity_id)
        REFERENCES activities (tenant_id, id),
    CONSTRAINT fk_activity_assignees_employee FOREIGN KEY (tenant_id, employee_id)
        REFERENCES employees (tenant_id, id),
    CONSTRAINT ux_activity_assignees_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT activity_assignees_version_nonnegative CHECK (version >= 0),
    CONSTRAINT activity_assignees_revocation_order CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT activity_assignees_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX ux_activity_assignees_active
    ON activity_assignees (tenant_id, activity_id, employee_id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_activity_assignees_employee_active
    ON activity_assignees (tenant_id, employee_id, activity_id)
    WHERE revoked_at IS NULL;

CREATE FUNCTION agriinsight_security.enforce_assignment_revocation_history()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF (to_jsonb(NEW) - ARRAY['revoked_at', 'version', 'updated_at'])
       IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['revoked_at', 'version', 'updated_at']) THEN
        RAISE EXCEPTION 'Assignment identity and creation history are immutable';
    END IF;

    IF OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'Assignment revocation is one-way';
    END IF;

    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'Assignment version must increment by one';
    END IF;

    IF NEW.updated_at < OLD.updated_at THEN
        RAISE EXCEPTION 'Assignment updated_at cannot move backwards';
    END IF;

    RETURN NEW;
END
$function$;

REVOKE ALL ON FUNCTION agriinsight_security.enforce_assignment_revocation_history() FROM PUBLIC;

CREATE TRIGGER user_farm_assignments_revoke_only
    BEFORE UPDATE ON user_farm_assignments
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.enforce_assignment_revocation_history();

CREATE TRIGGER activity_assignees_revoke_only
    BEFORE UPDATE ON activity_assignees
    FOR EACH ROW
    EXECUTE FUNCTION agriinsight_security.enforce_assignment_revocation_history();

CREATE TABLE activity_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    author_profile_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    notes VARCHAR(2000),
    quantity NUMERIC(18, 4),
    unit_code VARCHAR(24),
    evidence_uri VARCHAR(2048),
    corrects_log_id UUID,
    correction_kind VARCHAR(16),
    correction_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_logs_activity FOREIGN KEY (tenant_id, activity_id)
        REFERENCES activities (tenant_id, id),
    CONSTRAINT fk_activity_logs_employee FOREIGN KEY (tenant_id, employee_id)
        REFERENCES employees (tenant_id, id),
    CONSTRAINT fk_activity_logs_author FOREIGN KEY (tenant_id, author_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT ux_activity_logs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_activity_logs_tenant_activity_id UNIQUE (tenant_id, activity_id, id),
    CONSTRAINT fk_activity_logs_correction FOREIGN KEY (tenant_id, activity_id, corrects_log_id)
        REFERENCES activity_logs (tenant_id, activity_id, id),
    CONSTRAINT activity_logs_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> ''),
    CONSTRAINT activity_logs_quantity_unit CHECK (
        (quantity IS NULL AND unit_code IS NULL)
        OR (quantity IS NOT NULL AND quantity > 0 AND unit_code IS NOT NULL
            AND unit_code IN ('KG', 'TONNE', 'LITRE', 'HOUR', 'HECTARE', 'UNIT'))
    ),
    CONSTRAINT activity_logs_evidence_uri CHECK (
        evidence_uri IS NULL OR evidence_uri ~ '^(https|s3|gs|az)://[^[:space:]]+$'
    ),
    CONSTRAINT activity_logs_not_self_correction CHECK (corrects_log_id IS NULL OR corrects_log_id <> id),
    CONSTRAINT activity_logs_correction_shape CHECK (
        (corrects_log_id IS NULL AND correction_kind IS NULL AND correction_reason IS NULL)
        OR (corrects_log_id IS NOT NULL AND correction_kind IN ('REPLACE', 'VOID')
            AND correction_reason IS NOT NULL
            AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT activity_logs_void_payload CHECK (
        correction_kind IS DISTINCT FROM 'VOID'
        OR (quantity IS NULL AND unit_code IS NULL AND evidence_uri IS NULL)
    ),
    CONSTRAINT activity_logs_version_nonnegative CHECK (version >= 0),
    CONSTRAINT activity_logs_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_activity_logs_tenant_activity_occurred
    ON activity_logs (tenant_id, activity_id, occurred_at DESC, id);
CREATE INDEX ix_activity_logs_tenant_employee_occurred
    ON activity_logs (tenant_id, employee_id, occurred_at DESC, id);
CREATE INDEX ix_activity_logs_tenant_author_occurred
    ON activity_logs (tenant_id, author_profile_id, occurred_at DESC, id);
CREATE UNIQUE INDEX ux_activity_logs_single_successor
    ON activity_logs (tenant_id, corrects_log_id)
    WHERE corrects_log_id IS NOT NULL;

CREATE TABLE harvests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    field_id UUID NOT NULL,
    season_id UUID NOT NULL,
    crop_id UUID NOT NULL,
    recorded_by_profile_id UUID NOT NULL,
    occurred_on DATE NOT NULL,
    quantity_kg NUMERIC(18, 3) NOT NULL,
    waste_quantity_kg NUMERIC(18, 3) NOT NULL DEFAULT 0,
    quality_grade VARCHAR(64),
    revenue_vnd NUMERIC(19, 2),
    corrects_harvest_id UUID,
    correction_kind VARCHAR(16),
    correction_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_harvests_season FOREIGN KEY (tenant_id, farm_id, field_id, crop_id, season_id)
        REFERENCES seasons (tenant_id, farm_id, field_id, crop_id, id),
    CONSTRAINT fk_harvests_recorder FOREIGN KEY (tenant_id, recorded_by_profile_id)
        REFERENCES user_profiles (tenant_id, id),
    CONSTRAINT ux_harvests_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ux_harvests_tenant_hierarchy_id
        UNIQUE (tenant_id, farm_id, field_id, season_id, crop_id, id),
    CONSTRAINT fk_harvests_correction FOREIGN KEY (
        tenant_id, farm_id, field_id, season_id, crop_id, corrects_harvest_id)
        REFERENCES harvests (tenant_id, farm_id, field_id, season_id, crop_id, id),
    CONSTRAINT harvests_quantity_shape CHECK (
        (correction_kind = 'VOID'
            AND quantity_kg = 0
            AND waste_quantity_kg = 0
            AND quality_grade IS NULL
            AND revenue_vnd IS NULL)
        OR (correction_kind IS DISTINCT FROM 'VOID'
            AND quantity_kg > 0
            AND waste_quantity_kg >= 0
            AND waste_quantity_kg <= quantity_kg)
    ),
    CONSTRAINT harvests_quality_grade_nonblank CHECK (quality_grade IS NULL OR btrim(quality_grade) <> ''),
    CONSTRAINT harvests_revenue_nonnegative CHECK (revenue_vnd IS NULL OR revenue_vnd >= 0),
    CONSTRAINT harvests_not_self_correction CHECK (corrects_harvest_id IS NULL OR corrects_harvest_id <> id),
    CONSTRAINT harvests_correction_shape CHECK (
        (corrects_harvest_id IS NULL AND correction_kind IS NULL AND correction_reason IS NULL)
        OR (corrects_harvest_id IS NOT NULL AND correction_kind IN ('REPLACE', 'VOID')
            AND correction_reason IS NOT NULL AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT harvests_version_nonnegative CHECK (version >= 0),
    CONSTRAINT harvests_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX ix_harvests_tenant_farm_occurred
    ON harvests (tenant_id, farm_id, occurred_on DESC, id);
CREATE INDEX ix_harvests_tenant_field_occurred
    ON harvests (tenant_id, field_id, occurred_on DESC, id);
CREATE INDEX ix_harvests_tenant_season_occurred
    ON harvests (tenant_id, season_id, occurred_on DESC, id);
CREATE UNIQUE INDEX ux_harvests_single_successor
    ON harvests (tenant_id, corrects_harvest_id)
    WHERE corrects_harvest_id IS NOT NULL;

COMMENT ON TABLE farms IS 'Tenant farm master; canonical code remains reserved while inactive.';
COMMENT ON TABLE crops IS 'Tenant crop catalog used by seasons and harvest facts.';
COMMENT ON TABLE fields IS 'Tenant field master with farm, area, optional coordinates, and responsible employee.';
COMMENT ON TABLE seasons IS 'Field-bound crop season with lifecycle dates and non-cost budget comparison input.';
COMMENT ON COLUMN seasons.budget_vnd IS 'Optional comparison input for Phase 6; not an operating-cost fact.';
COMMENT ON TABLE employees IS 'Operational employee master without payroll or sensitive HR attributes.';
COMMENT ON TABLE user_farm_assignments IS 'Append-preserved user-to-farm authorization assignments.';
COMMENT ON TABLE activity_types IS 'Tenant catalog restricted to the fixed operational activity vocabulary.';
COMMENT ON TABLE activities IS 'Planned field-work tasks with an explicit lifecycle and immutable parent hierarchy.';
COMMENT ON TABLE activity_assignees IS 'Append-preserved employee assignment history for activities.';
COMMENT ON TABLE activity_logs IS 'Immutable activity evidence; corrections append rows linked to an earlier log.';
COMMENT ON COLUMN activity_logs.evidence_uri IS 'Bounded URI metadata only; the backend must never fetch this URI.';
COMMENT ON TABLE harvests IS 'Immutable normalized harvest facts; corrections append rows with unchanged hierarchy.';
COMMENT ON COLUMN harvests.quantity_kg IS 'Kilograms normalized at the API boundary; zero is reserved for an explicit VOID correction.';
COMMENT ON COLUMN harvests.revenue_vnd IS 'Gross harvest revenue only; operating costs remain Phase 6 data.';
