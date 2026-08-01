from __future__ import annotations

import pandas as pd
import pytest

from agriinsight.metrics_yield_forecast_contract import YIELD_FORECAST_GOLD_COLUMNS
from agriinsight.yield_forecast import MODEL_VERSION
from agriinsight.metrics_yield_forecast import (
    YieldForecastGoldError,
    build_yield_forecast_gold,
)
from tests.yield_forecast_gold_test_data import AS_OF_DATE, yield_forecast_warehouse


def test_gold_builder_is_deterministic_and_preserves_season_grain() -> None:
    connection = yield_forecast_warehouse()

    first = build_yield_forecast_gold(connection, AS_OF_DATE)
    second = build_yield_forecast_gold(connection, AS_OF_DATE)

    assert list(first.columns) == list(YIELD_FORECAST_GOLD_COLUMNS)
    pd.testing.assert_frame_equal(first, second)
    assert first["season_code"].tolist() == ["RICE-2026"]
    row = first.iloc[0]
    assert row["forecast_status"] == "ready"
    assert row["model_version"] == MODEL_VERSION
    assert row["history_seasons"] == 5
    assert row["backtest_origins"] == 2
    assert row["backtest_seasons"] == 2
    assert row["target_yield_kg"] == 20260.0
    assert row["forecast_quantity_kg"] == pytest.approx(
        row["forecast_yield_kg_per_ha"] * row["season_area_ha"]
    )


def test_gold_builder_rejects_harvest_dimension_mismatch() -> None:
    connection = yield_forecast_warehouse()
    connection.execute(
        "UPDATE fact_harvest SET farm_key = 2 WHERE harvest_id = 'HARVEST-2025-A'"
    )
    connection.commit()

    with pytest.raises(YieldForecastGoldError, match="relationship"):
        build_yield_forecast_gold(connection, AS_OF_DATE)


def test_gold_builder_preserves_nullable_target_context() -> None:
    connection = yield_forecast_warehouse()
    connection.execute(
        "UPDATE dim_season SET target_yield_kg = NULL WHERE season_code = 'RICE-2026'"
    )
    connection.commit()

    forecast = build_yield_forecast_gold(connection, AS_OF_DATE)

    assert pd.isna(forecast.loc[0, "target_yield_kg"])


@pytest.mark.parametrize("season_code", ("RICE-2025", "RICE-2026"))
def test_gold_builder_rejects_season_field_farm_mismatch(season_code: str) -> None:
    connection = yield_forecast_warehouse()
    connection.execute(
        "UPDATE dim_season SET field_key = 2 WHERE season_code = ?",
        (season_code,),
    )
    connection.commit()

    with pytest.raises(YieldForecastGoldError, match="field and farm"):
        build_yield_forecast_gold(connection, AS_OF_DATE)
