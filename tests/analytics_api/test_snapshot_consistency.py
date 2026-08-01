from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

import pandas as pd
import pytest

from agriinsight.analytics_api import snapshot_cache
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.snapshot_cache import SnapshotCache


def test_snapshot_cache_loads_once_per_manifest_fingerprint(
    analytics_artifact_root: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls = 0
    original = snapshot_cache.load_artifact_snapshot

    def counted(*args, **kwargs):
        nonlocal calls
        calls += 1
        return original(*args, **kwargs)

    monkeypatch.setattr(snapshot_cache, "load_artifact_snapshot", counted)
    cache = SnapshotCache(analytics_artifact_root)

    first = cache.current()
    second = cache.current()

    assert first is second
    assert calls == 1


def test_manifest_transition_invalidates_cache_and_old_response(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    cache = SnapshotCache(copied)
    first = cache.current()
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest_path.write_text(
        json.dumps(manifest, sort_keys=True),
        encoding="utf-8",
    )

    second = cache.current()

    assert second is not first
    assert second.manifest_fingerprint != first.manifest_fingerprint
    with pytest.raises(ApiProblem) as captured:
        cache.assert_current(first)
    assert captured.value.code == "snapshot_changed"


def test_corrupt_aggregate_failure_is_sanitized(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    (copied / "gold" / "executive_summary.csv").write_text(
        "private,path\nC:\\private\\secret,1\n",
        encoding="utf-8",
    )

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.status_code == 503
    assert "private" not in captured.value.safe_message
    assert "secret" not in captured.value.safe_message


def test_checksum_valid_but_malformed_csv_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "executive_summary.csv"
    frame = pd.read_csv(csv_path)
    frame["total_revenue_vnd"] = frame["total_revenue_vnd"].astype(object)
    frame.loc[0, "total_revenue_vnd"] = "not-a-number"
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/executive_summary.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_checksum_valid_but_nonfinite_forecast_evidence_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "inventory_status.csv"
    frame = pd.read_csv(csv_path)
    available = frame["forecast_coverage_status"] != "unavailable"
    assert available.any()
    frame.loc[available.idxmax(), "forecast_quantity"] = float("inf")
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/inventory_status.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_checksum_valid_but_wrong_forecast_decision_evidence_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "inventory_status.csv"
    frame = pd.read_csv(csv_path)
    available = frame["forecast_coverage_status"] != "unavailable"
    assert available.any()
    row_index = available.idxmax()
    frame.loc[row_index, "forecast_suggested_order_quantity"] = (
        float(frame.loc[row_index, "forecast_suggested_order_quantity"]) + 1.0
    )
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/inventory_status.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


@pytest.mark.parametrize(
    ("column", "value"),
    (
        ("forecast_history_start_date", "2099-01-01"),
        ("forecast_backtest_windows", 999),
    ),
)
def test_checksum_valid_but_impossible_forecast_history_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
    column: str,
    value: object,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "inventory_status.csv"
    frame = pd.read_csv(csv_path)
    available = frame["forecast_coverage_status"] != "unavailable"
    assert available.any()
    frame.loc[available.idxmax(), column] = value
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/inventory_status.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_checksum_valid_but_corrupt_yield_forecast_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "yield_forecast.csv"
    frame = pd.read_csv(csv_path)
    assert not frame.empty
    frame.loc[frame.index[0], "forecast_quantity_kg"] = float("inf")
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/yield_forecast.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_checksum_valid_but_timezone_yield_forecast_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "yield_forecast.csv"
    frame = pd.read_csv(csv_path)
    assert not frame.empty
    frame.loc[frame.index[0], "history_start_at"] = "2021-10-02T12:00:00+07:00"
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/yield_forecast.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_checksum_valid_but_nonfinite_yield_target_fails_contract(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    csv_path = copied / "gold" / "yield_forecast.csv"
    frame = pd.read_csv(csv_path)
    assert not frame.empty
    frame.loc[frame.index[0], "target_yield_kg"] = float("inf")
    frame.to_csv(csv_path, index=False)
    manifest_path = copied / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["checksums"]["gold/yield_forecast.csv"] = hashlib.sha256(
        csv_path.read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_contract_invalid"


def test_oversized_manifest_fails_closed(
    analytics_artifact_root: Path,
    tmp_path: Path,
) -> None:
    copied = tmp_path / "artifacts"
    shutil.copytree(analytics_artifact_root, copied)
    from agriinsight.analytics_snapshot import MAX_MANIFEST_BYTES

    (copied / "manifest.json").write_bytes(b"{" + b"x" * MAX_MANIFEST_BYTES)

    with pytest.raises(ApiProblem) as captured:
        SnapshotCache(copied).current()

    assert captured.value.code == "snapshot_unavailable"
