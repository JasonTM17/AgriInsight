from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

import pandas as pd

from agriinsight.cost_report_contract import CostReportRequest, ReportValidationError


@dataclass(frozen=True, slots=True)
class OperatingDashboardView:
    operating_total_cost_vnd: float
    budget_variance_vnd: float | None
    operating_cost_per_ha_vnd: float | None
    operating_cost_per_kg_vnd: float | None
    activity_drivers: pd.DataFrame
    detail: pd.DataFrame
    season_context: pd.DataFrame
    context_is_broader: bool


def _frame(gold: Mapping[str, pd.DataFrame], name: str) -> pd.DataFrame:
    try:
        return gold[name]
    except KeyError as error:
        raise ReportValidationError(f"Missing Gold dataset: {name}") from error


def _sum(frame: pd.DataFrame, column: str) -> float:
    return float(frame[column].sum()) if not frame.empty else 0.0


def _ratio(numerator: float, denominator: float, *, has_context: bool) -> float | None:
    if not has_context:
        return None
    return numerator / denominator if denominator else 0.0


def _operating_detail(
    gold: Mapping[str, pd.DataFrame], request: CostReportRequest
) -> pd.DataFrame:
    detail = _frame(gold, "cost_activity_detail")
    mask = pd.Series(True, index=detail.index, dtype=bool)
    for column, value in (
        ("farm_code", request.farm),
        ("crop_code", request.crop),
        ("season_code", request.season),
        ("activity_type", request.activity),
    ):
        if value:
            mask &= detail[column].eq(value)
    if request.month_from:
        mask &= detail["month"].ge(request.month_from)
    if request.month_to:
        mask &= detail["month"].le(request.month_to)
    return detail.loc[mask].sort_values(
        ["occurred_at", "activity_id"], kind="stable", ignore_index=True
    )


def _season_context(
    gold: Mapping[str, pd.DataFrame], request: CostReportRequest
) -> pd.DataFrame:
    seasons = _frame(gold, "cost_season")
    mask = pd.Series(True, index=seasons.index, dtype=bool)
    for column, value in (
        ("farm_code", request.farm),
        ("crop_code", request.crop),
        ("season_code", request.season),
    ):
        if value:
            mask &= seasons[column].eq(value)
    return seasons.loc[mask].sort_values(
        ["farm_code", "season_code", "field_code"],
        kind="stable",
        ignore_index=True,
    )


def _activity_drivers(detail: pd.DataFrame, top_n: int) -> pd.DataFrame:
    if detail.empty:
        return pd.DataFrame(
            columns=(
                "activity_type",
                "activity_count",
                "operating_material_cost_vnd",
                "operating_labor_cost_vnd",
                "operating_total_cost_vnd",
                "operating_cost_share_pct",
            )
        )
    drivers = (
        detail.groupby("activity_type", as_index=False, sort=True)
        .agg(
            activity_count=("activity_id", "count"),
            operating_material_cost_vnd=("operating_material_cost_vnd", "sum"),
            operating_labor_cost_vnd=("operating_labor_cost_vnd", "sum"),
            operating_total_cost_vnd=("operating_total_cost_vnd", "sum"),
        )
        .sort_values(
            ["operating_total_cost_vnd", "activity_type"],
            ascending=[False, True],
            kind="stable",
            ignore_index=True,
        )
        .head(top_n)
        .copy()
    )
    total = float(detail["operating_total_cost_vnd"].sum())
    drivers["operating_cost_share_pct"] = (
        100 * drivers["operating_total_cost_vnd"] / total if total else 0.0
    )
    return drivers


def prepare_operating_dashboard(
    gold: Mapping[str, pd.DataFrame], request: CostReportRequest
) -> OperatingDashboardView:
    if request.scope != "operating":
        raise ReportValidationError("Operating dashboard requires scope=operating")
    detail = _operating_detail(gold, request)
    seasons = _season_context(gold, request)
    context_total = _sum(seasons, "operating_total_cost_vnd")
    return OperatingDashboardView(
        operating_total_cost_vnd=_sum(detail, "operating_total_cost_vnd"),
        budget_variance_vnd=(
            _sum(seasons, "budget_variance_vnd") if not seasons.empty else None
        ),
        operating_cost_per_ha_vnd=_ratio(
            context_total, _sum(seasons, "area_ha"), has_context=not seasons.empty
        ),
        operating_cost_per_kg_vnd=_ratio(
            context_total,
            _sum(seasons, "harvest_quantity_kg"),
            has_context=not seasons.empty,
        ),
        activity_drivers=_activity_drivers(detail, request.top_n),
        detail=detail,
        season_context=seasons,
        context_is_broader=bool(
            request.activity or request.month_from or request.month_to
        ),
    )


__all__ = ["OperatingDashboardView", "prepare_operating_dashboard"]
