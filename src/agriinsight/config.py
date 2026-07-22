from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Mapping


DEFAULT_SEED = 20_260_718
DEFAULT_AS_OF_DATE = date(2026, 7, 18)
DEFAULT_SCALE_PROFILE = "standard"

SCALE_PROFILE_DEFAULTS: dict[str, dict[str, int]] = {
    "standard": {
        "farm_count": 6,
        "fields_per_farm": 4,
        "activities_per_season": 14,
        "material_count": 15,
        "sensor_history_days": 120,
        "sensor_readings_per_day": 4,
    },
    "big-data": {
        "farm_count": 10,
        "fields_per_farm": 12,
        "activities_per_season": 60,
        "material_count": 18,
        "sensor_history_days": 365,
        "sensor_readings_per_day": 24,
    },
}

CONFIGURATION_FIELDS = tuple(SCALE_PROFILE_DEFAULTS[DEFAULT_SCALE_PROFILE])


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
    scale_profile: str = DEFAULT_SCALE_PROFILE

    def __post_init__(self) -> None:
        if self.scale_profile not in SCALE_PROFILE_DEFAULTS:
            raise ValueError(
                f"scale_profile must be one of {sorted(SCALE_PROFILE_DEFAULTS)}"
            )
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

    @property
    def nominal_sensor_readings(self) -> int:
        """Return the planned reading count before intentional quality fixtures."""

        return (
            self.farm_count
            * self.fields_per_farm
            * self.sensor_history_days
            * self.sensor_readings_per_day
        )

    def resolved_dimensions(self) -> dict[str, int]:
        return {field: int(getattr(self, field)) for field in CONFIGURATION_FIELDS}

    def manifest_configuration(self) -> dict[str, str | int]:
        return {
            "scale_profile": self.scale_profile,
            **self.resolved_dimensions(),
            "nominal_sensor_readings": self.nominal_sensor_readings,
        }

    @property
    def run_id(self) -> str:
        """Return a deterministic identity for the fully resolved dataset."""

        identity = {
            "as_of_date": self.as_of_date.isoformat(),
            "seed": self.seed,
            "configuration": self.manifest_configuration(),
        }
        encoded = json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()
        fingerprint = hashlib.sha256(encoded).hexdigest()[:12]
        return (
            f"synthetic-{self.as_of_date.isoformat()}-{self.seed}-"
            f"{self.scale_profile}-{fingerprint}"
        )


def resolve_generation_config(
    profile: str = DEFAULT_SCALE_PROFILE,
    *,
    seed: int = DEFAULT_SEED,
    as_of_date: date = DEFAULT_AS_OF_DATE,
    overrides: Mapping[str, int | None] | None = None,
) -> GenerationConfig:
    """Resolve a named scale profile plus explicit CLI/test overrides."""

    try:
        dimensions = dict(SCALE_PROFILE_DEFAULTS[profile])
    except KeyError as error:
        raise ValueError(f"unknown scale profile: {profile}") from error
    for field, value in (overrides or {}).items():
        if field not in CONFIGURATION_FIELDS:
            raise ValueError(f"unknown GenerationConfig override: {field}")
        if value is not None:
            dimensions[field] = value
    return GenerationConfig(
        seed=seed,
        as_of_date=as_of_date,
        scale_profile=profile,
        **dimensions,
    )


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
