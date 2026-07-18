from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import pandas as pd

from agriinsight.config import ArtifactPaths, GenerationConfig
from agriinsight.insights import build_insights
from agriinsight.metrics import build_gold_datasets
from agriinsight.quality import build_quality_report
from agriinsight.synthetic import generate_bronze
from agriinsight.transform import clean_bronze
from agriinsight.warehouse import build_warehouse


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    temp_path.replace(path)


def _write_tables(tables: dict[str, pd.DataFrame], directory: Path) -> None:
    for table_name, frame in tables.items():
        frame.to_csv(directory / f"{table_name}.csv", index=False, encoding="utf-8")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact_checksums(root: Path) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for path in sorted(root.rglob("*")):
        if (
            not path.is_file()
            or path.name == "manifest.json"
            or path.suffix in {".tmp", ".log"}
        ):
            continue
        checksums[path.relative_to(root).as_posix()] = _sha256(path)
    return checksums


def run_pipeline(output_root: Path, config: GenerationConfig) -> dict[str, Any]:
    """Run the deterministic Bronze-to-Gold MVP pipeline."""

    paths = ArtifactPaths(output_root.resolve())
    paths.ensure()

    raw = generate_bronze(config)
    _write_tables(raw, paths.bronze)

    transform_result = clean_bronze(raw)
    _write_tables(transform_result.silver, paths.silver)
    _write_tables(transform_result.quarantine, paths.quarantine)

    quality_report = build_quality_report(
        raw,
        transform_result.silver,
        transform_result.actions,
        config.as_of_date,
    )
    _write_json(paths.quality / "data_quality_report.json", quality_report)
    if quality_report["status"] != "passed":
        raise RuntimeError("Silver data did not pass quality gates; warehouse load aborted")

    warehouse_report = build_warehouse(
        transform_result.silver,
        paths.warehouse / "agriinsight.db",
        config,
    )
    _write_json(paths.warehouse / "load_report.json", warehouse_report)

    gold = build_gold_datasets(paths.warehouse / "agriinsight.db")
    _write_tables(gold, paths.gold)
    insight_report = build_insights(gold, config.as_of_date)
    _write_json(paths.gold / "insights.json", insight_report)

    manifest = {
        "pipeline": "agriinsight-bronze-silver-gold-v1",
        "run_id": f"synthetic-{config.as_of_date.isoformat()}-{config.seed}",
        "as_of_date": config.as_of_date.isoformat(),
        "seed": config.seed,
        "configuration": {
            "farm_count": config.farm_count,
            "fields_per_farm": config.fields_per_farm,
            "activities_per_season": config.activities_per_season,
            "material_count": config.material_count,
            "sensor_history_days": config.sensor_history_days,
            "sensor_readings_per_day": config.sensor_readings_per_day,
        },
        "quality_status": quality_report["status"],
        "row_counts": {
            "bronze": {name: len(frame) for name, frame in raw.items()},
            "silver": {
                name: len(frame) for name, frame in transform_result.silver.items()
            },
            "quarantine": {
                name: len(frame) for name, frame in transform_result.quarantine.items()
            },
            "gold": {name: len(frame) for name, frame in gold.items()},
        },
        "warehouse": warehouse_report,
        "checksums": _artifact_checksums(paths.root),
    }
    _write_json(paths.root / "manifest.json", manifest)
    return manifest
