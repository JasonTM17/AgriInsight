from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from pathlib import Path


DEFAULT_SEED = 20_260_718
DEFAULT_AS_OF_DATE = date(2026, 7, 18)


@dataclass(frozen=True)
class GenerationConfig:
    """Controls the size and reproducibility of the synthetic source data."""

    seed: int = DEFAULT_SEED
    as_of_date: date = DEFAULT_AS_OF_DATE
    farm_count: int = 6
    fields_per_farm: int = 4
    activities_per_season: int = 14
    material_count: int = 15
    sensor_history_days: int = 120
    sensor_readings_per_day: int = 4

    def __post_init__(self) -> None:
        if not 1 <= self.farm_count <= 10:
            raise ValueError("farm_count must be between 1 and 10")
        if not 1 <= self.fields_per_farm <= 20:
            raise ValueError("fields_per_farm must be between 1 and 20")
        if not 4 <= self.activities_per_season <= 200:
            raise ValueError("activities_per_season must be between 4 and 200")
        if not 5 <= self.material_count <= 18:
            raise ValueError("material_count must be between 5 and 18")
        if not 14 <= self.sensor_history_days <= 730:
            raise ValueError("sensor_history_days must be between 14 and 730")
        if not 1 <= self.sensor_readings_per_day <= 24:
            raise ValueError("sensor_readings_per_day must be between 1 and 24")
        if self.as_of_date < date(2026, 1, 1):
            raise ValueError("as_of_date must be on or after 2026-01-01")


@dataclass(frozen=True)
class ArtifactPaths:
    root: Path

    @property
    def bronze(self) -> Path:
        return self.root / "bronze"

    @property
    def silver(self) -> Path:
        return self.root / "silver"

    @property
    def quarantine(self) -> Path:
        return self.root / "quarantine"

    @property
    def quality(self) -> Path:
        return self.root / "quality"

    @property
    def warehouse(self) -> Path:
        return self.root / "warehouse"

    @property
    def gold(self) -> Path:
        return self.root / "gold"

    def ensure(self) -> None:
        for path in (
            self.root,
            self.bronze,
            self.silver,
            self.quarantine,
            self.quality,
            self.warehouse,
            self.gold,
        ):
            path.mkdir(parents=True, exist_ok=True)
