from __future__ import annotations

from copy import deepcopy
from pathlib import Path

import pytest

from agriinsight.demo_tenant_contract import (
    load_demo_contract,
    load_demo_snapshot,
)
from agriinsight.demo_tenant_reconciliation import (
    expected_operational_state,
    reconcile_catalog,
)

CONTRACT = Path("deploy/demo/demo-tenant.json")


@pytest.fixture
def reconciliation_inputs(analytics_artifact_root: Path):
    contract = load_demo_contract(CONTRACT)
    snapshot = load_demo_snapshot(analytics_artifact_root)
    return contract, snapshot, expected_operational_state(contract, snapshot)


def test_exact_verified_catalog_passes(reconciliation_inputs) -> None:
    contract, snapshot, actual = reconciliation_inputs

    report = reconcile_catalog(contract, snapshot, actual)

    assert report["status"] == "passed"
    assert report["errorCount"] == 0
    assert report["counts"] == {
        "farms": 3,
        "fields": 6,
        "crops": 5,
        "seasons": 12,
        "warehouses": 3,
        "materials": 5,
        "suppliers": 8,
        "personas": 7,
    }


@pytest.mark.parametrize(
    ("domain", "mutate", "expected_fragment"),
    [
        ("farms", lambda rows: rows.pop(), "missing"),
        ("fields", lambda rows: rows.append(deepcopy(rows[0])), "got 2"),
        (
            "crops",
            lambda rows: rows[0].update(active=False),
            "active expected True",
        ),
        (
            "seasons",
            lambda rows: rows[0].update(fieldCode="FIELD-FOREIGN"),
            "fieldCode expected",
        ),
        (
            "warehouses",
            lambda rows: rows.append({"code": "WH-FOREIGN", "active": True}),
            "unexpected",
        ),
        ("suppliers", lambda rows: rows.pop(), "missing"),
        (
            "personas",
            lambda rows: rows[0].update(role="INVALID_ROLE"),
            "role expected",
        ),
    ],
)
def test_catalog_drift_fails_closed(
    reconciliation_inputs,
    domain,
    mutate,
    expected_fragment,
) -> None:
    contract, snapshot, actual = reconciliation_inputs
    changed = deepcopy(actual)
    mutate(changed[domain])

    report = reconcile_catalog(contract, snapshot, changed)

    assert report["status"] == "failed"
    assert report["errorCount"] >= 1
    assert any(
        expected_fragment in error
        for error in report["domains"][domain]["errors"]
    )
