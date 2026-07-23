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
