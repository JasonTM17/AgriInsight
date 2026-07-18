from __future__ import annotations

import json
from datetime import date

import pandas as pd
import pytest

from agriinsight.config import GenerationConfig
from agriinsight.pipeline import run_pipeline


@pytest.fixture(scope="session")
def report_sources(tmp_path_factory: pytest.TempPathFactory):
    root = tmp_path_factory.mktemp("cost-report") / "artifacts"
    run_pipeline(
        root,
        GenerationConfig(
            seed=73,
            as_of_date=date(2026, 7, 18),
            farm_count=2,
            fields_per_farm=2,
            activities_per_season=6,
            material_count=5,
            sensor_history_days=14,
            sensor_readings_per_day=1,
        ),
    )
    gold = {path.stem: pd.read_csv(path) for path in (root / "gold").glob("*.csv")}
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    return gold, manifest
