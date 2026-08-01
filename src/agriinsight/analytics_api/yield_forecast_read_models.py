from __future__ import annotations

from datetime import date

import pandas as pd

from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_api.errors import ApiProblem
from agriinsight.analytics_api.filter_scope import AppliedAnalyticsFilter
from agriinsight.analytics_api.models import (
    PageModel,
    YieldForecastHealthModel,
    YieldForecastPayload,
)
from agriinsight.analytics_api.record_models import YieldForecastModel
from agriinsight.analytics_api.response_shaping import records
from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.metrics_yield_forecast_contract import (
    ACTIVE_SEASON_COLUMNS,
    validate_yield_forecast_gold,
)
from agriinsight.metrics_yield_forecast_validation import dates

_PUBLIC_FORECAST_STATUS = {
    "ready": "ready",
    "insufficient_history": "insufficientHistory",
}
_SEASON_CONTEXT_COLUMNS = {
    "farm_code",
    "field_code",
    "season_code",
    "crop_code",
    "season_status",
    "start_date",
    "expected_harvest_date",
    "area_ha",
    "target_yield_kg",
}


def yield_forecast_payload(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    applied_filter: AppliedAnalyticsFilter,
    limit: int,
    offset: int,
) -> YieldForecastPayload:
    forecast = _validated_forecast_rows(snapshot)
    scoped = forecast.loc[forecast["farm_code"].isin(scope.farm_codes)].copy()
    scoped = _apply_filter(scoped, applied_filter)
    scoped = scoped.sort_values(
        ["expected_harvest_date", "season_code"],
        ascending=[True, True],
        kind="stable",
    )
    page = scoped.iloc[offset : offset + limit]
    return YieldForecastPayload(
        forecast_health=_forecast_health(scoped),
        items=_strict_public_records(page),
        page=PageModel(
            has_more=offset + limit < len(scoped),
            limit=limit,
            offset=offset,
            total=len(scoped),
        ),
    )


def _validated_forecast_rows(snapshot: ArtifactSnapshot) -> pd.DataFrame:
    try:
        as_of = date.fromisoformat(str(snapshot.manifest["as_of_date"]))
        forecast = snapshot.csv["yield_forecast"]
        validate_yield_forecast_gold(
            forecast,
            as_of,
            _eligible_seasons(snapshot.csv["cost_season"], as_of),
        )
    except (
        AttributeError,
        KeyError,
        TypeError,
        ValueError,
    ) as error:
        raise ApiProblem(
            503,
            "snapshot_contract_invalid",
            "The verified analytics snapshot does not match the API contract.",
        ) from error
    return forecast


def _eligible_seasons(cost_season: pd.DataFrame, as_of: date) -> pd.DataFrame:
    if not _SEASON_CONTEXT_COLUMNS.issubset(cost_season.columns):
        raise ValueError("cost season context is incomplete")
    start_dates = dates(cost_season["start_date"], "cost season start")
    expected_dates = dates(
        cost_season["expected_harvest_date"],
        "cost season expected harvest",
    )
    eligible = cost_season.loc[
        cost_season["season_status"].eq("active")
        & (start_dates <= as_of)
        & (expected_dates > as_of),
        [
            "farm_code",
            "field_code",
            "season_code",
            "crop_code",
            "start_date",
            "expected_harvest_date",
            "area_ha",
            "target_yield_kg",
        ],
    ].rename(
        columns={
            "start_date": "season_start_date",
            "area_ha": "season_area_ha",
        }
    )
    return eligible.loc[:, ACTIVE_SEASON_COLUMNS]


def _apply_filter(
    forecast: pd.DataFrame,
    applied_filter: AppliedAnalyticsFilter,
) -> pd.DataFrame:
    for column, value in (
        ("farm_code", applied_filter.farm_code),
        ("field_code", applied_filter.field_code),
        ("crop_code", applied_filter.crop_code),
        ("season_code", applied_filter.season_code),
    ):
        if value is not None:
            forecast = forecast.loc[forecast[column].eq(value)]
    return forecast


def _forecast_health(forecast: pd.DataFrame) -> YieldForecastHealthModel:
    status = forecast["forecast_status"]
    return YieldForecastHealthModel(
        ready=int(status.eq("ready").sum()),
        insufficient_history=int(status.eq("insufficient_history").sum()),
        total=int(len(forecast)),
    )


def _strict_public_records(page: pd.DataFrame) -> list[YieldForecastModel]:
    try:
        return [
            YieldForecastModel.model_validate(
                {
                    **record,
                    "forecastStatus": _PUBLIC_FORECAST_STATUS[
                        record["forecastStatus"]
                    ],
                }
            )
            for record in records(page)
        ]
    except (KeyError, TypeError, ValueError) as error:
        raise ApiProblem(
            503,
            "snapshot_contract_invalid",
            "The verified analytics snapshot does not match the API contract.",
        ) from error
