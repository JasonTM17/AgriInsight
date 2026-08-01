from __future__ import annotations

from pathlib import Path
from types import MappingProxyType
from datetime import datetime, timezone
from uuid import uuid4

import pandas as pd
import pytest

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_bootstrap import (
    create_demo_bundle,
    write_demo_bundle,
)
from agriinsight.demo_tenant_contract import load_demo_contract
from agriinsight.demo_tenant_master_sql import master_catalog_sql
from agriinsight.demo_tenant_sample_sql import (
    FIELD_WORKER_EMPLOYEE_CODE,
    WORK_ASSIGNMENT_LIMIT,
    select_activity_samples,
)
from agriinsight.demo_tenant_sql_primitives import deterministic_id, master_upsert

CONTRACT = Path("deploy/demo/demo-tenant.json")


def test_demo_bundle_is_deterministic_bounded_and_credential_free(
    analytics_artifact_root: Path,
) -> None:
    manifest_before = (analytics_artifact_root / "manifest.json").read_bytes()

    first = create_demo_bundle(analytics_artifact_root, CONTRACT)
    second = create_demo_bundle(analytics_artifact_root, CONTRACT)

    assert first == second
    assert first.sample_activity_count == 24
    assert first.seed_sql.count("INSERT INTO user_profiles") == 7
    assert first.seed_sql.count("INSERT INTO external_identities") == 7
    assert first.seed_sql.count("INSERT INTO employees") == 1
    assert (
        first.seed_sql.count("INSERT INTO activity_assignees")
        == WORK_ASSIGNMENT_LIMIT
    )
    assert "INSERT INTO activity_logs" not in first.seed_sql
    assert "current_database() <> 'agriinsight_demo'" in first.seed_sql
    assert (
        "current_setting('app.agriinsight_demo_database', TRUE) <> 'true'"
        in first.seed_sql
    )
    assert "SET LOCAL app.demo_bootstrap_confirmed" not in first.seed_sql
    assert first.seed_sql.startswith("\\set ON_ERROR_STOP on\nBEGIN;")
    assert first.seed_sql.rstrip().endswith(
        "-- Idempotent local-demo seed completed."
    )
    assert first.seed_sql.index("INSERT INTO farms") < first.seed_sql.index(
        "INSERT INTO user_farm_assignments"
    )
    lowered = first.seed_sql.lower()
    assert "password" not in lowered
    assert "sk-" not in lowered
    assert (analytics_artifact_root / "manifest.json").read_bytes() == manifest_before


def test_demo_bundle_links_field_worker_and_assigns_exact_sample_prefix(
    analytics_artifact_root: Path,
) -> None:
    bundle = create_demo_bundle(analytics_artifact_root, CONTRACT)
    contract = load_demo_contract(CONTRACT)
    field_worker = next(
        persona for persona in contract.personas if persona.role == "FIELD_WORKER"
    )
    employee_id = deterministic_id("employees", FIELD_WORKER_EMPLOYEE_CODE)
    employee_sql = master_upsert(
        "employees",
        contract,
        FIELD_WORKER_EMPLOYEE_CODE,
        {
            "display_name": field_worker.display_name,
            "job_title": "Field Worker",
            "active": True,
        },
    )

    assert employee_sql in bundle.seed_sql
    assert (
        "INSERT INTO user_profiles "
        "(id, tenant_id, employee_id, display_name, email, active) VALUES "
        f"('{field_worker.profile_id}'::uuid, '{contract.tenant_id}'::uuid, "
        f"'{employee_id}'::uuid"
    ) in bundle.seed_sql

    activities = (
        analytics_artifact_root / "silver" / "activities.csv"
    )
    supported = select_activity_samples(
        pd.read_csv(activities),
        pd.read_csv(analytics_artifact_root / "silver" / "seasons.csv"),
        WORK_ASSIGNMENT_LIMIT + 1,
    )
    assigned = supported.head(WORK_ASSIGNMENT_LIMIT)
    for row in assigned.itertuples(index=False):
        assignment_id = deterministic_id(
            "activity-assignment",
            f"{employee_id}:{row.activity_id}",
        )
        activity_id = deterministic_id("activities", str(row.activity_id))
        assert f"SELECT '{assignment_id}'::uuid" in bundle.seed_sql
        assert f"AND activity.id = '{activity_id}'::uuid" in bundle.seed_sql
        assert (
            "ON CONFLICT (id) DO NOTHING;"
        ) in bundle.seed_sql
        assert (
            f"WHERE existing.id = '{assignment_id}'::uuid "
            "AND ("
            f"existing.tenant_id <> '{contract.tenant_id}'::uuid "
            f"OR existing.activity_id <> '{activity_id}'::uuid "
            f"OR existing.employee_id <> '{employee_id}'::uuid"
        ) in bundle.seed_sql

    assert "UPDATE activity_assignees AS existing" not in bundle.seed_sql
    assert "demo activity assignment id is bound elsewhere" in bundle.seed_sql

    unassigned = supported.iloc[WORK_ASSIGNMENT_LIMIT]
    unassigned_id = deterministic_id(
        "activity-assignment",
        f"{employee_id}:{unassigned.activity_id}",
    )
    unassigned_activity_id = deterministic_id(
        "activities",
        str(unassigned.activity_id),
    )
    assert str(unassigned_id) not in bundle.seed_sql
    assert (
        f"'{unassigned_activity_id}'::uuid, '{contract.tenant_id}'::uuid"
        in bundle.seed_sql
    )
    unassigned_activity_sql = next(
        line
        for line in bundle.seed_sql.splitlines()
        if line.startswith("INSERT INTO activities")
        and str(unassigned_activity_id) in line
    )
    assert "'PLANNED'" in unassigned_activity_sql
    assert unassigned_activity_sql.count("NULL") >= 3
    live_seasons = pd.read_csv(
        analytics_artifact_root / "silver" / "seasons.csv"
    )
    parent_status = live_seasons.loc[
        live_seasons["season_code"] == unassigned.season_code,
        "status",
    ].iloc[0]
    assert str(parent_status).upper() in {"PLANNED", "ACTIVE"}


def test_demo_bundle_with_zero_samples_has_no_activity_assignments(
    analytics_artifact_root: Path,
) -> None:
    bundle = create_demo_bundle(
        analytics_artifact_root,
        CONTRACT,
        sample_activity_limit=0,
    )

    assert bundle.sample_activity_count == 0
    assert "INSERT INTO activity_assignees" not in bundle.seed_sql
    assert "INSERT INTO employees" in bundle.seed_sql


def test_demo_bundle_requires_confirmation_and_repository_local_tmp(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    with pytest.raises(ValueError, match="confirmation"):
        write_demo_bundle(
            analytics_artifact_root,
            CONTRACT,
            Path("D:/AgriInsight/_tmp/test-output"),
            confirmed=False,
        )
    with pytest.raises(ValueError, match="repository-local"):
        write_demo_bundle(
            analytics_artifact_root,
            CONTRACT,
            Path("C:/unsafe/demo-output"),
            confirmed=True,
        )


def test_demo_bundle_caps_operational_samples(
    analytics_artifact_root: Path,
) -> None:
    with pytest.raises(ValueError, match="between 0 and 100"):
        create_demo_bundle(
            analytics_artifact_root,
            CONTRACT,
            sample_activity_limit=101,
        )


def test_demo_bundle_seeds_supplier_master_from_silver_artifact(
    analytics_artifact_root: Path,
) -> None:
    bundle = create_demo_bundle(analytics_artifact_root, CONTRACT)
    contract = load_demo_contract(CONTRACT)
    suppliers = pd.read_csv(analytics_artifact_root / "silver" / "suppliers.csv")

    assert len(suppliers) > 0
    assert bundle.seed_sql.count("INSERT INTO suppliers") == len(suppliers)
    for row in suppliers.itertuples(index=False):
        expected_sql = master_upsert(
            "suppliers",
            contract,
            row.supplier_code,
            {"display_name": row.supplier_name, "active": True},
        )
        assert expected_sql in bundle.seed_sql


def test_master_catalog_uses_completed_at_and_season_area_snapshot() -> None:
    contract = load_demo_contract(CONTRACT)
    snapshot = ArtifactSnapshot(
        csv=MappingProxyType(
            {
                "farms": pd.DataFrame(
                    [{"farm_code": "FARM-001", "farm_name": "Farm 1"}]
                ),
                "crops": pd.DataFrame(
                    [{"crop_code": "RICE", "crop_name": "Rice"}]
                ),
                "fields": pd.DataFrame(
                    [
                        {
                            "field_code": "FIELD-001",
                            "farm_code": "FARM-001",
                            "field_name": "Field 1",
                            "area_ha": 99.0,
                            "latitude": 10.1,
                            "longitude": 105.2,
                            "soil_type": "Loam",
                            "irrigation_type": "Drip",
                        }
                    ]
                ),
                "seasons": pd.DataFrame(
                    [
                        {
                            "season_code": "SEASON-2025-0001",
                            "field_code": "FIELD-001",
                            "crop_code": "RICE",
                            "start_date": "2025-01-10",
                            "expected_harvest_date": "2025-05-10",
                            "season_area_ha": 12.5,
                            "completed_at": "2025-05-12T18:30:00",
                            "budget_cost_vnd": 1_000_000,
                            "status": "completed",
                        }
                    ]
                ),
                "warehouses": pd.DataFrame(
                    [{"warehouse_code": "WH-001", "farm_code": "FARM-001", "warehouse_name": "Main"}]
                ),
                "materials": pd.DataFrame(
                    [
                        {
                            "material_code": "MAT-001",
                            "material_name": "Material 1",
                            "base_unit": "kg",
                            "reorder_point": 10.0,
                        }
                    ]
                ),
                "suppliers": pd.DataFrame(
                    [{"supplier_code": "SUP-001", "supplier_name": "Supplier 1"}]
                ),
            }
        ),
        generated_at=datetime(2026, 7, 18, tzinfo=timezone.utc),
        json=MappingProxyType({}),
        manifest=MappingProxyType({}),
        manifest_fingerprint=str(uuid4()),
        source_fingerprint=str(uuid4()),
    )

    season_sql = next(
        line
        for line in master_catalog_sql(contract, snapshot)
        if line.startswith("INSERT INTO seasons")
    )

    assert "'2025-05-12'" in season_sql
    assert "'2025-05-12T18:30:00'" not in season_sql
    assert ", 12.5, 1000000, 'COMPLETED')" in season_sql
    assert "99.0" not in season_sql


def test_artifact_text_cannot_be_promoted_to_raw_sql() -> None:
    contract = load_demo_contract(CONTRACT)

    statement = master_upsert(
        "farms",
        contract,
        "FARM-SQL-GUARD",
        {"display_name": "(SELECT id FROM attacker_controlled_relation)"},
    )

    assert "'(SELECT id FROM attacker_controlled_relation)'" in statement
