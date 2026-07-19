from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest

from dashboard import cost_analysis_snapshot
from dashboard.cost_analysis_snapshot import (
    CostSnapshotError,
    load_cost_analysis_snapshot,
)


def _write_snapshot(root: Path, *, run_id: str = "run-a") -> tuple[bytes, bytes]:
    gold = root / "gold"
    gold.mkdir(parents=True)
    cost = b"farm_code,total_cost\nFARM-001,125\n"
    procurement = b"supplier_code,procurement_spend\nSUP-001,75\n"
    (gold / "cost_summary.csv").write_bytes(cost)
    (gold / "procurement_detail.csv").write_bytes(procurement)
    manifest = {
        "run_id": run_id,
        "checksums": {
            "gold/cost_summary.csv": hashlib.sha256(cost).hexdigest(),
            "gold/procurement_detail.csv": hashlib.sha256(procurement).hexdigest(),
        },
    }
    manifest_bytes = json.dumps(manifest, sort_keys=True).encode("utf-8")
    (root / "manifest.json").write_bytes(manifest_bytes)
    return manifest_bytes, json.dumps(
        {**manifest, "run_id": "run-b"}, sort_keys=True
    ).encode("utf-8")


def test_snapshot_parses_the_same_bytes_it_verifies(tmp_path: Path) -> None:
    _write_snapshot(tmp_path)

    snapshot = load_cost_analysis_snapshot(
        tmp_path, ("cost_summary", "procurement_detail")
    )

    assert snapshot.manifest["run_id"] == "run-a"
    assert snapshot.gold["cost_summary"].iloc[0]["total_cost"] == 125
    assert len(snapshot.source_fingerprint) == 64


def test_snapshot_rejects_stable_checksum_mismatch(tmp_path: Path) -> None:
    _write_snapshot(tmp_path)
    cost_path = tmp_path / "gold" / "cost_summary.csv"
    cost_path.write_bytes(cost_path.read_bytes() + b"FARM-002,50\n")

    with pytest.raises(CostSnapshotError, match="checksum-verified"):
        load_cost_analysis_snapshot(
            tmp_path, ("cost_summary", "procurement_detail")
        )


def test_snapshot_retries_one_manifest_transition(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    manifest_a, manifest_b = _write_snapshot(tmp_path)
    manifest_reads = iter((manifest_a, manifest_b, manifest_b, manifest_b))

    def transition_read(path: Path) -> bytes:
        if path.name == "manifest.json":
            return next(manifest_reads)
        return path.read_bytes()

    monkeypatch.setattr(cost_analysis_snapshot, "_read_bytes", transition_read)

    snapshot = load_cost_analysis_snapshot(
        tmp_path, ("cost_summary", "procurement_detail")
    )

    assert snapshot.manifest["run_id"] == "run-b"
