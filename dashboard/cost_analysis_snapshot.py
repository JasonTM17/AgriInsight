from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from typing import Mapping, Sequence

import pandas as pd


class CostSnapshotError(RuntimeError):
    """Raised when Cost Gold and its manifest are not one verified snapshot."""


@dataclass(frozen=True, slots=True)
class CostAnalysisSnapshot:
    gold: Mapping[str, pd.DataFrame]
    manifest: Mapping[str, object]
    source_fingerprint: str


def _read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def _manifest(data: bytes) -> dict[str, object]:
    payload = json.loads(data)
    if not isinstance(payload, dict):
        raise CostSnapshotError("Manifest root must be a JSON object")
    return payload


def _snapshot_once(
    root: Path, dataset_names: Sequence[str]
) -> CostAnalysisSnapshot | None:
    manifest_path = root / "manifest.json"
    manifest_before = _read_bytes(manifest_path)
    manifest = _manifest(manifest_before)
    checksums = manifest.get("checksums")
    if not isinstance(checksums, Mapping):
        raise CostSnapshotError("Manifest checksums are required for Cost Gold")

    frames: dict[str, pd.DataFrame] = {}
    actual_checksums: dict[str, str] = {}
    mismatches: list[str] = []
    for name in sorted(dataset_names):
        relative_path = f"gold/{name}.csv"
        content = _read_bytes(root / relative_path)
        actual = hashlib.sha256(content).hexdigest()
        expected = checksums.get(relative_path)
        if not isinstance(expected, str) or expected != actual:
            mismatches.append(relative_path)
        actual_checksums[relative_path] = actual
        frames[name] = pd.read_csv(BytesIO(content))

    manifest_after = _read_bytes(manifest_path)
    if manifest_before != manifest_after:
        return None
    if mismatches:
        raise CostSnapshotError(
            "Cost Gold checksum mismatch: " + ", ".join(mismatches)
        )
    fingerprint_payload = json.dumps(
        actual_checksums, sort_keys=True, separators=(",", ":")
    )
    return CostAnalysisSnapshot(
        gold=frames,
        manifest=manifest,
        source_fingerprint=hashlib.sha256(
            fingerprint_payload.encode("ascii")
        ).hexdigest(),
    )


def load_cost_analysis_snapshot(
    root: Path, dataset_names: Sequence[str]
) -> CostAnalysisSnapshot:
    """Read and verify one stable Cost Gold snapshot, retrying one transition."""
    last_error: Exception | None = None
    for _ in range(2):
        try:
            snapshot = _snapshot_once(root, dataset_names)
            if snapshot is not None:
                return snapshot
            last_error = CostSnapshotError("Manifest changed while Cost Gold was read")
        except (
            CostSnapshotError,
            OSError,
            UnicodeError,
            json.JSONDecodeError,
            pd.errors.EmptyDataError,
            pd.errors.ParserError,
        ) as error:
            last_error = error
    raise CostSnapshotError(
        "Unable to read one stable, checksum-verified Cost Gold snapshot"
    ) from last_error


__all__ = [
    "CostAnalysisSnapshot",
    "CostSnapshotError",
    "load_cost_analysis_snapshot",
]
