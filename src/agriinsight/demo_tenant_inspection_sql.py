from __future__ import annotations

from agriinsight.demo_tenant_contract import DemoContract
from agriinsight.demo_tenant_sql_primitives import literal


def inspection_sql(contract: DemoContract) -> str:
    tenant = literal(str(contract.tenant_id))
    profile_ids = ", ".join(
        f"{literal(str(persona.profile_id))}::uuid"
        for persona in contract.personas
    )
    return f"""\\set ON_ERROR_STOP on
BEGIN;
SET LOCAL app.tenant_id = {tenant};
SELECT json_build_object(
  'farms', (SELECT coalesce(json_agg(json_build_object(
    'code', code, 'active', active) ORDER BY code), '[]'::json)
    FROM farms WHERE tenant_id = {tenant}::uuid),
  'fields', (SELECT coalesce(json_agg(json_build_object(
    'code', field.code, 'active', field.active, 'farmCode', farm.code)
    ORDER BY field.code), '[]'::json)
    FROM fields field
    JOIN farms farm ON farm.tenant_id = field.tenant_id AND farm.id = field.farm_id
    WHERE field.tenant_id = {tenant}::uuid),
  'crops', (SELECT coalesce(json_agg(json_build_object(
    'code', code, 'active', active) ORDER BY code), '[]'::json)
    FROM crops WHERE tenant_id = {tenant}::uuid),
  'seasons', (SELECT coalesce(json_agg(json_build_object(
    'code', season.code, 'active', season.status <> 'CANCELLED',
    'fieldCode', field.code, 'cropCode', crop.code) ORDER BY season.code), '[]'::json)
    FROM seasons season
    JOIN fields field ON field.tenant_id = season.tenant_id AND field.id = season.field_id
    JOIN crops crop ON crop.tenant_id = season.tenant_id AND crop.id = season.crop_id
    WHERE season.tenant_id = {tenant}::uuid),
  'warehouses', (SELECT coalesce(json_agg(json_build_object(
    'code', code, 'active', active) ORDER BY code), '[]'::json)
    FROM warehouses WHERE tenant_id = {tenant}::uuid),
  'materials', (SELECT coalesce(json_agg(json_build_object(
    'code', code, 'active', active) ORDER BY code), '[]'::json)
    FROM materials WHERE tenant_id = {tenant}::uuid),
  'suppliers', (SELECT coalesce(json_agg(json_build_object(
    'code', code, 'active', active) ORDER BY code), '[]'::json)
    FROM suppliers WHERE tenant_id = {tenant}::uuid),
  'personas', (SELECT coalesce(json_agg(json_build_object(
    'profileId', profile.id::text,
    'active', profile.active AND identity_row.active AND role_row.revoked_at IS NULL,
    'role', role_row.role_code,
    'issuer', identity_row.issuer,
    'subject', identity_row.subject,
    'farmCodes', coalesce((SELECT json_agg(farm.code ORDER BY farm.code)
      FROM user_farm_assignments assignment
      JOIN farms farm ON farm.tenant_id = assignment.tenant_id
        AND farm.id = assignment.farm_id
      WHERE assignment.tenant_id = profile.tenant_id
        AND assignment.user_profile_id = profile.id
        AND assignment.revoked_at IS NULL), '[]'::json),
    'warehouseCodes', coalesce((SELECT json_agg(warehouse.code ORDER BY warehouse.code)
      FROM user_warehouse_assignments assignment
      JOIN warehouses warehouse ON warehouse.tenant_id = assignment.tenant_id
        AND warehouse.id = assignment.warehouse_id
      WHERE assignment.tenant_id = profile.tenant_id
        AND assignment.user_profile_id = profile.id
        AND assignment.revoked_at IS NULL), '[]'::json))
    ORDER BY profile.id), '[]'::json)
    FROM user_profiles profile
    JOIN external_identities identity_row
      ON identity_row.tenant_id = profile.tenant_id
      AND identity_row.user_profile_id = profile.id
    JOIN user_roles role_row
      ON role_row.tenant_id = profile.tenant_id
      AND role_row.user_profile_id = profile.id
    WHERE profile.tenant_id = {tenant}::uuid
      AND profile.id IN ({profile_ids}))
)::text;
COMMIT;
"""
