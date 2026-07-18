from __future__ import annotations

import argparse
import json
from datetime import date
from pathlib import Path
from typing import Sequence

from agriinsight import __version__
from agriinsight.config import DEFAULT_AS_OF_DATE, DEFAULT_SEED, GenerationConfig
from agriinsight.pipeline import run_pipeline


def _iso_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("expected an ISO date such as 2026-07-18") from error


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="agriinsight",
        description="AgriInsight data analytics MVP",
    )
    parser.add_argument("--version", action="version", version=__version__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    run = subparsers.add_parser("run", help="run the Bronze-to-Gold pipeline")
    run.add_argument("--output", type=Path, default=Path("artifacts"))
    run.add_argument("--seed", type=int, default=DEFAULT_SEED)
    run.add_argument("--as-of-date", type=_iso_date, default=DEFAULT_AS_OF_DATE)
    run.add_argument("--farms", type=int, default=6)
    run.add_argument("--fields-per-farm", type=int, default=4)
    run.add_argument("--activities-per-season", type=int, default=14)
    run.add_argument("--materials", type=int, default=15)
    run.add_argument("--sensor-days", type=int, default=120)
    run.add_argument("--sensor-readings-per-day", type=int, default=4)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _parser().parse_args(argv)
    if arguments.command == "run":
        config = GenerationConfig(
            seed=arguments.seed,
            as_of_date=arguments.as_of_date,
            farm_count=arguments.farms,
            fields_per_farm=arguments.fields_per_farm,
            activities_per_season=arguments.activities_per_season,
            material_count=arguments.materials,
            sensor_history_days=arguments.sensor_days,
            sensor_readings_per_day=arguments.sensor_readings_per_day,
        )
        manifest = run_pipeline(arguments.output, config)
        summary = {
            "run_id": manifest["run_id"],
            "quality_status": manifest["quality_status"],
            "output": str(arguments.output.resolve()),
            "warehouse_rows": manifest["warehouse"]["row_counts"],
        }
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
