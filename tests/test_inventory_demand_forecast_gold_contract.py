from __future__ import annotations

from datetime import date, timedelta

import numpy as np
import pandas as pd
import pytest

from agriinsight.inventory_demand_forecast import FORECAST_COLUMNS, MODEL_VERSION
from agriinsight.metrics_inventory_forecast import (
    InventoryDemandForecastGoldError,
    attach_inventory_demand_forecast,
)
from agriinsight.metrics_inventory_forecast_status_contract import (
    validate_inventory_forecast_status,
)


AS_OF = date(2026, 6, 29)


def _forecast_frame(**overrides: object) -> pd.DataFrame:
    row = {
        "as_of_date": AS_OF.isoformat(),
        "warehouse_code": "WH-001",
        "material_code": "MAT-001",
        "base_unit": "kg",
        "model_version": MODEL_VERSION,
        "forecast_status": "ready",
        "history_start_date": (AS_OF - timedelta(days=179)).isoformat(),
        "history_end_date": AS_OF.isoformat(),
        "history_days": 180,
        "nonzero_demand_days": 180,
        "horizon_days": 30,
        "forecast_quantity": 30.0,
        "lower_quantity": 20.0,
        "upper_quantity": 50.0,
        "backtest_windows": 9,
        "backtest_mae": 1.0,
        "backtest_wape_pct": 5.0,
        **overrides,
    }
    return pd.DataFrame([row], columns=FORECAST_COLUMNS)


def _status_frame() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "warehouse_code": "WH-001",
                "material_code": "MAT-001",
                "base_unit": "kg",
                "stock_quantity": 10.0,
                "recommended_order_quantity": 99.0,
                "predicted_30d_need": 777.0,
            }
        ]
    )


@pytest.mark.parametrize(
    ("forecast", "message"),
    (
        (
            pd.concat([_forecast_frame(), _forecast_frame()], ignore_index=True),
            "duplicate",
        ),
        (_forecast_frame(as_of_date="2026-06-28"), "as-of"),
        (_forecast_frame(history_end_date="2026-06-28"), "history end"),
        (_forecast_frame(base_unit="liter"), "base unit"),
        (_forecast_frame(warehouse_code="WH-999"), "inventory status"),
        (_forecast_frame(forecast_quantity=float("inf")), "finite"),
        (_forecast_frame(upper_quantity=float("nan")), "finite"),
        (_forecast_frame(horizon_days=31), "history or horizon"),
        (
            _forecast_frame(history_start_date="2099-01-01"),
            "history date span",
        ),
        (
            _forecast_frame(backtest_windows=999),
            "backtest window count",
        ),
        (_forecast_frame(backtest_mae=None), "backtest evidence"),
        (
            _forecast_frame(
                forecast_status="no_demand",
                backtest_windows=0,
                backtest_mae=None,
                backtest_wape_pct=None,
            ),
            "status evidence",
        ),
    ),
)
def test_join_rejects_forecast_contract_drift(
    forecast: pd.DataFrame,
    message: str,
) -> None:
    with pytest.raises(InventoryDemandForecastGoldError, match=message):
        attach_inventory_demand_forecast(_status_frame(), forecast, AS_OF)


@pytest.mark.parametrize(
    ("mutate", "message"),
    (
        (
            lambda frame: pd.concat([frame, frame], ignore_index=True),
            "duplicate",
        ),
        (
            lambda frame: frame.assign(stock_quantity=float("nan")),
            "stock values must be finite",
        ),
        (
            lambda frame: frame.assign(forecast_quantity=1.0),
            "already contains forecast evidence",
        ),
    ),
)
def test_join_rejects_inventory_status_contract_drift(
    mutate,
    message: str,
) -> None:
    with pytest.raises(InventoryDemandForecastGoldError, match=message):
        attach_inventory_demand_forecast(
            mutate(_status_frame()),
            _forecast_frame(),
            AS_OF,
        )


def test_join_rejects_nonfinite_derived_days_of_supply() -> None:
    status = _status_frame()
    status.loc[0, "stock_quantity"] = np.finfo(float).max
    forecast = _forecast_frame(
        forecast_quantity=np.nextafter(0.0, 1.0),
        lower_quantity=0.0,
        upper_quantity=1.0,
    )

    with pytest.raises(InventoryDemandForecastGoldError, match="finite"):
        attach_inventory_demand_forecast(status, forecast, AS_OF)


@pytest.mark.parametrize(
    ("column", "message"),
    (
        (
            "forecast_days_of_supply",
            "days of supply is inconsistent",
        ),
        (
            "forecast_suggested_order_quantity",
            "suggested order quantity is inconsistent",
        ),
    ),
)
def test_joined_contract_rejects_finite_but_wrong_decision_evidence(
    column: str,
    message: str,
) -> None:
    joined = attach_inventory_demand_forecast(
        _status_frame(),
        _forecast_frame(),
        AS_OF,
    )
    joined.loc[0, column] = 999.0

    with pytest.raises(InventoryDemandForecastGoldError, match=message):
        validate_inventory_forecast_status(joined, AS_OF.isoformat())


def test_joined_contract_rejects_large_absolute_order_drift() -> None:
    joined = attach_inventory_demand_forecast(
        _status_frame(),
        _forecast_frame(upper_quantity=1e15),
        AS_OF,
    )
    joined.loc[0, "forecast_suggested_order_quantity"] += 500.0

    with pytest.raises(
        InventoryDemandForecastGoldError,
        match="suggested order quantity is inconsistent",
    ):
        validate_inventory_forecast_status(joined, AS_OF.isoformat())
