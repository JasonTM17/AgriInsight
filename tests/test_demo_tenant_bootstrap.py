from __future__ import annotations

from pathlib import Path

import pytest

from agriinsight.demo_tenant_bootstrap import (
    create_demo_bundle,
    write_demo_bundle,
)
from agriinsight.demo_tenant_contract import load_demo_contract
from agriinsight.demo_tenant_sql_primitives import master_upsert

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


def test_demo_bundle_requires_confirmation_and_d_local_tmp(
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
    with pytest.raises(ValueError, match="D-local"):
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


def test_artifact_text_cannot_be_promoted_to_raw_sql() -> None:
    contract = load_demo_contract(CONTRACT)

    statement = master_upsert(
        "farms",
        contract,
        "FARM-SQL-GUARD",
        {"display_name": "(SELECT id FROM attacker_controlled_relation)"},
    )

    assert "'(SELECT id FROM attacker_controlled_relation)'" in statement
