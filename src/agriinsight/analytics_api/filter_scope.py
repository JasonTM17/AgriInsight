from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from typing import Literal

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.models import AppliedFilterModel
from agriinsight.analytics_snapshot import ArtifactSnapshot

DatePreset = Literal["all", "last-30-days", "season-to-date"]


@dataclass(frozen=True, slots=True)
class AppliedAnalyticsFilter:
    crop_code: str | None
    date_from: date | None
    date_preset: DatePreset
    date_to: date
    farm_code: str | None
    field_code: str | None
    season_code: str | None

    @property
    def is_filtered(self) -> bool:
        return self.date_preset != "all" or any(
            (
                self.farm_code,
                self.field_code,
                self.crop_code,
                self.season_code,
            )
        )

    @property
    def requires_event_aggregation(self) -> bool:
        return self.date_preset != "all" or any(
            (self.field_code, self.crop_code, self.season_code)
        )

    def response_model(self) -> AppliedFilterModel:
        return AppliedFilterModel(
            crop_code=self.crop_code,
            date_from=str(self.date_from) if self.date_from else None,
            date_preset=self.date_preset,
            date_to=str(self.date_to),
            farm_code=self.farm_code,
            field_code=self.field_code,
            season_code=self.season_code,
        )


def resolve_analytics_filter(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    farm_code: str | None,
    field_code: str | None,
    crop_code: str | None,
    season_code: str | None,
    date_preset: DatePreset,
) -> AppliedAnalyticsFilter:
    relations = _scoped_relations(snapshot, scope)
    requested = {
        "farm_code": farm_code,
        "field_code": field_code,
        "crop_code": crop_code,
        "season_code": season_code,
    }
    for column, value in requested.items():
        if value is not None and not (relations[column] == value).any():
            raise ApiProblem(
                403,
                "analytics_filter_forbidden",
                "The requested filter is outside the verified analytics scope.",
            )

    selected = relations
    for column, value in requested.items():
        if value is not None:
            selected = selected[selected[column] == value]
    if selected.empty and any(requested.values()):
        raise ApiProblem(
            422,
            "analytics_filter_conflict",
            "The requested analytics filters do not share one verified relationship.",
        )

    as_of = _as_of(snapshot)
    if date_preset == "last-30-days":
        date_from = as_of - timedelta(days=29)
    elif date_preset == "season-to-date":
        if season_code is None:
            raise ApiProblem(
                422,
                "season_filter_required",
                "season-to-date requires one verified season filter.",
            )
        season_start = pd.to_datetime(
            selected.loc[selected["season_code"] == season_code, "start_date"],
            errors="raise",
        ).dt.date
        date_from = min(season_start)
        if date_from > as_of:
            raise ApiProblem(
                422,
                "season_not_started",
                "The selected season has not started at the snapshot cutoff.",
            )
    else:
        date_from = None

    return AppliedAnalyticsFilter(
        crop_code=crop_code,
        date_from=date_from,
        date_preset=date_preset,
        date_to=as_of,
        farm_code=farm_code,
        field_code=field_code,
        season_code=season_code,
    )


def selected_relationships(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    applied: AppliedAnalyticsFilter,
) -> pd.DataFrame:
    selected = _scoped_relations(snapshot, scope)
    for column, value in (
        ("farm_code", applied.farm_code),
        ("field_code", applied.field_code),
        ("crop_code", applied.crop_code),
        ("season_code", applied.season_code),
    ):
        if value is not None:
            selected = selected[selected[column] == value]
    return selected.copy()


def _scoped_relations(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
) -> pd.DataFrame:
    relations = snapshot.csv["cost_season"]
    return relations[relations["farm_code"].isin(scope.farm_codes)]


def _as_of(snapshot: ArtifactSnapshot) -> date:
    try:
        return date.fromisoformat(str(snapshot.manifest["as_of_date"]))
    except (KeyError, TypeError, ValueError) as error:
        raise ApiProblem(
            503,
            "snapshot_contract_invalid",
            "The verified analytics snapshot has an invalid cutoff.",
        ) from error
