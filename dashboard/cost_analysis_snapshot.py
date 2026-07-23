from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

import pandas as pd

from agriinsight.analytics_snapshot import (
    ArtifactSnapshotError,
    load_artifact_snapshot,
)


class CostSnapshotError(ArtifactSnapshotError):
    """Raised when Cost Gold and its manifest are not one verified snapshot."""


@dataclass(frozen=True, slots=True)
class CostAnalysisSnapshot:
    gold: Mapping[str, pd.DataFrame]
    manifest: Mapping[str, object]
    source_fingerprint: str


def load_cost_analysis_snapshot(
    root: Path, dataset_names: Sequence[str]
) -> CostAnalysisSnapshot:
    """Compatibility wrapper over the shared artifact trust boundary."""
    try:
        snapshot = load_artifact_snapshot(
            root,
            csv_datasets={
                name: f"gold/{name}.csv" for name in dataset_names
            },
        )
    except ArtifactSnapshotError as error:
        raise CostSnapshotError(
            "Unable to read one stable, checksum-verified Cost Gold snapshot"
        ) from error
    return CostAnalysisSnapshot(
        gold=snapshot.csv,
        manifest=snapshot.manifest,
        source_fingerprint=snapshot.source_fingerprint,
    )


__all__ = [
    "CostAnalysisSnapshot",
    "CostSnapshotError",
    "load_cost_analysis_snapshot",
]
