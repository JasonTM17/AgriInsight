from __future__ import annotations

from dataclasses import dataclass
from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_contract import DemoContract
from agriinsight.demo_tenant_inspection_sql import inspection_sql
from agriinsight.demo_tenant_master_sql import master_catalog_sql
from agriinsight.demo_tenant_sample_sql import activity_sample_sql
from agriinsight.demo_tenant_sql_primitives import deterministic_id, literal


@dataclass(frozen=True, slots=True)
class DemoSqlBundle:
    inspection_sql: str
    sample_activity_count: int
    seed_sql: str


def build_sql_bundle(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
    *,
    sample_activity_limit: int = 24,
) -> DemoSqlBundle:
    if not 0 <= sample_activity_limit <= 100:
        raise ValueError("Sample activity limit must be between 0 and 100")
    lines = _preamble(contract)
    lines.extend(master_catalog_sql(contract, snapshot))
    lines.extend(_persona_sql(contract))
    activity_lines, activity_count = activity_sample_sql(
        contract,
        snapshot,
        sample_activity_limit,
    )
    lines.extend(activity_lines)
    lines.extend(
        [
            "COMMIT;",
            "-- Idempotent local-demo seed completed.",
            "",
        ]
    )
    return DemoSqlBundle(
        inspection_sql=inspection_sql(contract),
        sample_activity_count=activity_count,
        seed_sql="\n".join(lines),
    )


def _preamble(contract: DemoContract) -> list[str]:
    tenant = literal(str(contract.tenant_id))
    return [
        r"\set ON_ERROR_STOP on",
        "BEGIN;",
        f"SET LOCAL app.tenant_id = {tenant};",
        "DO $guard$",
        "BEGIN",
        "  IF current_database() <> 'agriinsight_demo' THEN",
        "    RAISE EXCEPTION 'demo bootstrap requires agriinsight_demo';",
        "  END IF;",
        (
            "  IF current_setting("
            "'app.agriinsight_demo_database', TRUE) <> 'true' THEN"
        ),
        "    RAISE EXCEPTION 'demo database server marker missing';",
        "  END IF;",
        "END",
        "$guard$;",
        (
            "INSERT INTO tenants (id, code, display_name, active) VALUES "
            f"({tenant}::uuid, {literal(contract.tenant_code)}, "
            f"{literal(contract.tenant_display_name)}, TRUE) "
            "ON CONFLICT (code) DO UPDATE SET "
            "display_name = EXCLUDED.display_name, active = TRUE, "
            "updated_at = CURRENT_TIMESTAMP;"
        ),
        (
            "DO $tenant_guard$ BEGIN IF NOT EXISTS "
            f"(SELECT 1 FROM tenants WHERE id = {tenant}::uuid "
            f"AND code = {literal(contract.tenant_code)}) THEN "
            "RAISE EXCEPTION 'demo tenant code is bound to another id'; "
            "END IF; END $tenant_guard$;"
        ),
    ]


def _persona_sql(contract: DemoContract) -> list[str]:
    tenant = literal(str(contract.tenant_id))
    lines: list[str] = []
    for persona in contract.personas:
        profile = literal(str(persona.profile_id))
        identity_id = literal(
            str(deterministic_id("external-identity", persona.subject))
        )
        role_id = literal(
            str(deterministic_id("user-role", str(persona.profile_id)))
        )
        lines.extend(
            [
                (
                    "INSERT INTO user_profiles "
                    "(id, tenant_id, display_name, email, active) VALUES "
                    f"({profile}::uuid, {tenant}::uuid, "
                    f"{literal(persona.display_name)}, {literal(persona.email)}, TRUE) "
                    "ON CONFLICT (id) DO UPDATE SET "
                    "display_name = EXCLUDED.display_name, email = EXCLUDED.email, "
                    "active = TRUE, updated_at = CURRENT_TIMESTAMP;"
                ),
                (
                    "INSERT INTO external_identities "
                    "(id, tenant_id, user_profile_id, issuer, subject, active) VALUES "
                    f"({identity_id}::uuid, {tenant}::uuid, {profile}::uuid, "
                    f"{literal(contract.issuer)}, {literal(persona.subject)}, TRUE) "
                    "ON CONFLICT (issuer, subject) DO NOTHING;"
                ),
                (
                    "DO $identity_guard$ BEGIN IF NOT EXISTS "
                    "(SELECT 1 FROM external_identities "
                    f"WHERE issuer = {literal(contract.issuer)} "
                    f"AND subject = {literal(persona.subject)} "
                    f"AND tenant_id = {tenant}::uuid "
                    f"AND user_profile_id = {profile}::uuid AND active = TRUE) "
                    "THEN RAISE EXCEPTION 'demo identity is bound elsewhere'; "
                    "END IF; END $identity_guard$;"
                ),
                (
                    "INSERT INTO user_roles "
                    "(id, tenant_id, user_profile_id, role_code, revoked_at) VALUES "
                    f"({role_id}::uuid, {tenant}::uuid, {profile}::uuid, "
                    f"{literal(persona.role)}, NULL) "
                    "ON CONFLICT (tenant_id, user_profile_id, role_code) "
                    "DO UPDATE SET revoked_at = NULL, updated_at = CURRENT_TIMESTAMP;"
                ),
            ]
        )
        for code in persona.farm_codes:
            assignment_id = literal(
                str(
                    deterministic_id(
                        "farm-assignment", f"{persona.profile_id}:{code}"
                    )
                )
            )
            lines.append(
                "INSERT INTO user_farm_assignments "
                "(id, tenant_id, user_profile_id, farm_id, revoked_at) "
                f"SELECT {assignment_id}::uuid, {tenant}::uuid, {profile}::uuid, "
                "farm.id, NULL FROM farms AS farm "
                f"WHERE farm.tenant_id = {tenant}::uuid "
                f"AND farm.code = {literal(code)} "
                "AND NOT EXISTS (SELECT 1 FROM user_farm_assignments AS existing "
                f"WHERE existing.tenant_id = {tenant}::uuid "
                f"AND existing.user_profile_id = {profile}::uuid "
                "AND existing.farm_id = farm.id AND existing.revoked_at IS NULL);"
            )
        for code in persona.warehouse_codes:
            assignment_id = literal(
                str(
                    deterministic_id(
                        "warehouse-assignment", f"{persona.profile_id}:{code}"
                    )
                )
            )
            lines.append(
                "INSERT INTO user_warehouse_assignments "
                "(id, tenant_id, user_profile_id, warehouse_id, revoked_at) "
                f"SELECT {assignment_id}::uuid, {tenant}::uuid, {profile}::uuid, "
                "warehouse.id, NULL FROM warehouses AS warehouse "
                f"WHERE warehouse.tenant_id = {tenant}::uuid "
                f"AND warehouse.code = {literal(code)} "
                "AND NOT EXISTS (SELECT 1 "
                "FROM user_warehouse_assignments AS existing "
                f"WHERE existing.tenant_id = {tenant}::uuid "
                f"AND existing.user_profile_id = {profile}::uuid "
                "AND existing.warehouse_id = warehouse.id "
                "AND existing.revoked_at IS NULL);"
            )
    return lines
