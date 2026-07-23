from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import date, datetime, time, timezone
from io import BytesIO
from pathlib import Path, PurePosixPath
from types import MappingProxyType
from typing import Any, Mapping

import pandas as pd

MAX_DATASET_BYTES = 64 * 1024 * 1024
MAX_MANIFEST_BYTES = 8 * 1024 * 1024


class ArtifactSnapshotError(RuntimeError):
    """Raised when selected artifacts are not one stable verified snapshot."""


@dataclass(frozen=True, slots=True)
class ArtifactSnapshot:
    csv: Mapping[str, pd.DataFrame]
    generated_at: datetime
    json: Mapping[str, Any]
    manifest: Mapping[str, Any]
    manifest_fingerprint: str
    source_fingerprint: str


def _read_bytes(path: Path) -> bytes:
    return path.read_bytes()


def _read_bounded_bytes(path: Path, limit: int) -> bytes:
    if path.stat().st_size > limit:
        raise ArtifactSnapshotError("Artifact file exceeds the safe byte limit")
    return _read_bytes(path)


def _safe_relative_path(value: str) -> PurePosixPath:
    if "\\" in value:
        raise ArtifactSnapshotError("Artifact dataset path is invalid")
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or ".." in path.parts:
        raise ArtifactSnapshotError("Artifact dataset path is invalid")
    if path.parts[0] not in {"bronze", "gold", "quality", "silver"}:
        raise ArtifactSnapshotError("Artifact dataset path is outside a trusted layer")
    return path


def _resolved_artifact_path(
    root: Path,
    relative_path: PurePosixPath,
) -> Path:
    candidate = root.joinpath(*relative_path.parts).resolve(strict=True)
    trusted_layer = (root / relative_path.parts[0]).resolve(strict=True)
    if (
        not candidate.is_relative_to(root)
        or not candidate.is_relative_to(trusted_layer)
    ):
        raise ArtifactSnapshotError("Artifact dataset path escapes its trusted layer")
    return candidate


def _parse_manifest(data: bytes) -> dict[str, Any]:
    payload = json.loads(data)
    if not isinstance(payload, dict):
        raise ArtifactSnapshotError("Manifest root must be a JSON object")
    if not isinstance(payload.get("checksums"), Mapping):
        raise ArtifactSnapshotError("Manifest checksums are required")
    return payload


def _snapshot_generated_at(
    manifest: Mapping[str, Any],
    manifest_path: Path,
) -> datetime:
    try:
        generated = manifest.get("generated_at")
        if isinstance(generated, str):
            parsed = datetime.fromisoformat(generated.replace("Z", "+00:00"))
            return (
                parsed.replace(tzinfo=timezone.utc)
                if parsed.tzinfo is None
                else parsed.astimezone(timezone.utc)
            )
        as_of = manifest.get("as_of_date")
        if isinstance(as_of, str):
            return datetime.combine(
                date.fromisoformat(as_of),
                time.min,
                timezone.utc,
            )
        return datetime.fromtimestamp(
            manifest_path.stat().st_mtime,
            tz=timezone.utc,
        )
    except ValueError as error:
        raise ArtifactSnapshotError("Manifest timestamp is invalid") from error


def _verified_content(
    root: Path,
    manifest: Mapping[str, Any],
    relative_path: PurePosixPath,
) -> tuple[bytes, str]:
    content = _read_bounded_bytes(
        _resolved_artifact_path(root, relative_path),
        MAX_DATASET_BYTES,
    )
    actual = hashlib.sha256(content).hexdigest()
    expected = manifest["checksums"].get(relative_path.as_posix())
    if not isinstance(expected, str) or expected != actual:
        raise ArtifactSnapshotError(
            f"Artifact checksum mismatch: {relative_path.as_posix()}"
        )
    return content, actual


def _snapshot_once(
    root: Path,
    csv_datasets: Mapping[str, str],
    json_datasets: Mapping[str, str],
) -> ArtifactSnapshot | None:
    manifest_path = root / "manifest.json"
    manifest_before = _read_bounded_bytes(manifest_path, MAX_MANIFEST_BYTES)
    manifest = _parse_manifest(manifest_before)
    generated_at = _snapshot_generated_at(manifest, manifest_path)

    frames: dict[str, pd.DataFrame] = {}
    documents: dict[str, Any] = {}
    actual_checksums: dict[str, str] = {}
    for name, value in sorted(csv_datasets.items()):
        relative_path = _safe_relative_path(value)
        content, checksum = _verified_content(root, manifest, relative_path)
        frames[name] = pd.read_csv(BytesIO(content))
        frames[name].flags.allows_duplicate_labels = False
        actual_checksums[relative_path.as_posix()] = checksum
    for name, value in sorted(json_datasets.items()):
        relative_path = _safe_relative_path(value)
        content, checksum = _verified_content(root, manifest, relative_path)
        documents[name] = json.loads(content)
        actual_checksums[relative_path.as_posix()] = checksum

    manifest_after = _read_bounded_bytes(manifest_path, MAX_MANIFEST_BYTES)
    if manifest_before != manifest_after:
        return None
    manifest_fingerprint = hashlib.sha256(manifest_before).hexdigest()
    fingerprint_payload = json.dumps(
        {
            "manifest": manifest_fingerprint,
            "sources": actual_checksums,
        },
        sort_keys=True,
        separators=(",", ":"),
    ).encode("ascii")
    return ArtifactSnapshot(
        csv=MappingProxyType(frames),
        generated_at=generated_at,
        json=MappingProxyType(documents),
        manifest=MappingProxyType(manifest),
        manifest_fingerprint=manifest_fingerprint,
        source_fingerprint=hashlib.sha256(fingerprint_payload).hexdigest(),
    )


def load_artifact_snapshot(
    root: Path,
    *,
    csv_datasets: Mapping[str, str],
    json_datasets: Mapping[str, str] | None = None,
) -> ArtifactSnapshot:
    """Read selected files from one stable manifest and verify their checksums."""
    root = root.resolve()
    last_error: Exception | None = None
    for _ in range(2):
        try:
            snapshot = _snapshot_once(root, csv_datasets, json_datasets or {})
            if snapshot is not None:
                return snapshot
            last_error = ArtifactSnapshotError(
                "Manifest changed while artifacts were read"
            )
        except (
            ArtifactSnapshotError,
            OSError,
            UnicodeError,
            json.JSONDecodeError,
            pd.errors.EmptyDataError,
            pd.errors.ParserError,
        ) as error:
            last_error = error
    raise ArtifactSnapshotError(
        "Unable to read one stable, checksum-verified artifact snapshot"
    ) from last_error


__all__ = [
    "ArtifactSnapshot",
    "ArtifactSnapshotError",
    "load_artifact_snapshot",
]
