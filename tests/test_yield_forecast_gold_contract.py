from __future__ import annotations

import pytest

from agriinsight.metrics_yield_forecast import (
    active_yield_forecast_seasons,
    build_yield_forecast_gold,
)
from agriinsight.metrics_yield_forecast_contract import (
    YieldForecastGoldContractError,
    validate_yield_forecast_gold,
)
from tests.yield_forecast_gold_test_data import AS_OF_DATE, yield_forecast_warehouse


def test_contract_accepts_exact_active_season_evidence() -> None:
    connection = yield_forecast_warehouse()
    forecast = build_yield_forecast_gold(connection, AS_OF_DATE)

    validate_yield_forecast_gold(
        forecast,
        AS_OF_DATE,
        active_yield_forecast_seasons(connection, AS_OF_DATE),
    )


@pytest.mark.parametrize(
    ("column", "value", "message"),
    (
        ("forecast_status", "unavailable", "status"),
        ("forecast_quantity_kg", float("inf"), "finite"),
        ("target_yield_kg", float("inf"), "finite"),
        ("observed_min_yield_kg_per_ha", 999.0, "range"),
        ("forecast_origin_date", "2026-06-30", "origin"),
    ),
)
def test_contract_rejects_corrupt_forecast_evidence(
    column: str,
    value: object,
    message: str,
) -> None:
    connection = yield_forecast_warehouse()
    forecast = build_yield_forecast_gold(connection, AS_OF_DATE)
    forecast.loc[0, column] = value

    with pytest.raises(YieldForecastGoldContractError, match=message):
        validate_yield_forecast_gold(
            forecast,
            AS_OF_DATE,
            active_yield_forecast_seasons(connection, AS_OF_DATE),
        )


def test_contract_rejects_missing_active_season_coverage() -> None:
    connection = yield_forecast_warehouse()
    forecast = build_yield_forecast_gold(connection, AS_OF_DATE).iloc[0:0]

    with pytest.raises(YieldForecastGoldContractError, match="coverage"):
        validate_yield_forecast_gold(
            forecast,
            AS_OF_DATE,
            active_yield_forecast_seasons(connection, AS_OF_DATE),
        )
