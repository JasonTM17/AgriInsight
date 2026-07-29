from __future__ import annotations

from datetime import date, timedelta

import pandas as pd
import pytest

from agriinsight.inventory_demand_forecast import (
    InventoryDemandForecastError,
    forecast_inventory_demand,
)


INPUT_COLUMNS = (
    "transaction_date",
    "warehouse_code",
    "material_code",
    "base_unit",
    "transaction_type",
    "quantity_base_unit",
)


def _movement(
    transaction_date: date,
    quantity: object,
    *,
    warehouse_code: str = "WH-001",
    material_code: str = "MAT-NPK",
    base_unit: str = "kg",
    transaction_type: str = "OUT",
) -> dict[str, object]:
    return {
        "transaction_date": transaction_date.isoformat(),
        "warehouse_code": warehouse_code,
        "material_code": material_code,
        "base_unit": base_unit,
        "transaction_type": transaction_type,
        "quantity_base_unit": quantity,
    }


def _frame(rows: list[dict[str, object]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=INPUT_COLUMNS)


@pytest.mark.parametrize(
    ("field", "value", "message"),
    (
        ("quantity_base_unit", -1.0, "non-negative finite numbers"),
        ("quantity_base_unit", float("nan"), "non-negative finite numbers"),
        ("quantity_base_unit", True, "non-negative finite numbers"),
        ("quantity_base_unit", "1.5", "non-negative finite numbers"),
        ("transaction_type", "ADJUST", "IN or OUT"),
        ("warehouse_code", " ", "non-blank identifiers"),
        ("transaction_date", "not-a-date", "valid timezone-free dates"),
        ("transaction_date", "today", "valid timezone-free dates"),
        ("transaction_date", "now", "valid timezone-free dates"),
        ("transaction_date", None, "valid timezone-free dates"),
        ("transaction_date", float("nan"), "valid timezone-free dates"),
        ("transaction_date", pd.NaT, "valid timezone-free dates"),
        ("transaction_date", 0, "valid timezone-free dates"),
        (
            "transaction_date",
            "2026-01-01T00:00:00Z",
            "valid timezone-free dates",
        ),
    ),
)
def test_invalid_movement_contract_fails_closed(
    field: str,
    value: object,
    message: str,
) -> None:
    row = _movement(date(2026, 1, 1), 1.0)
    row[field] = value

    with pytest.raises(InventoryDemandForecastError, match=message):
        forecast_inventory_demand(_frame([row]), date(2026, 6, 29))


def test_material_cannot_change_base_unit_within_a_warehouse() -> None:
    rows = [
        _movement(date(2026, 1, 1), 1.0, base_unit="kg"),
        _movement(date(2026, 1, 2), 1.0, base_unit="liter"),
    ]

    with pytest.raises(InventoryDemandForecastError, match="single base unit"):
        forecast_inventory_demand(_frame(rows), date(2026, 6, 29))


def test_duplicate_required_columns_fail_with_domain_error() -> None:
    frame = _frame([_movement(date(2026, 1, 1), 1.0)])
    duplicate_date_column = pd.concat(
        [frame, frame[["transaction_date"]]],
        axis=1,
    )

    with pytest.raises(InventoryDemandForecastError, match="column names must be unique"):
        forecast_inventory_demand(duplicate_date_column, date(2026, 6, 29))


def test_malformed_non_date_fields_outside_window_cannot_change_forecast() -> None:
    as_of = date(2026, 6, 29)
    start = as_of - timedelta(days=179)
    rows = [
        _movement(start + timedelta(days=offset), 1.0)
        for offset in range(180)
    ]
    malformed_old = _movement(start - timedelta(days=1), float("nan"))
    malformed_old["warehouse_code"] = " "
    malformed_future = _movement(as_of + timedelta(days=1), "not-numeric")
    malformed_future["transaction_type"] = "ADJUST"

    baseline = forecast_inventory_demand(_frame(rows), as_of)
    with_outside_rows = forecast_inventory_demand(
        _frame([*rows, malformed_old, malformed_future]),
        as_of,
    )

    assert baseline.to_dict("records") == with_outside_rows.to_dict("records")


def test_mixed_timezone_dates_fail_with_domain_error() -> None:
    rows = [
        _movement(date(2026, 1, 1), 1.0),
        _movement(date(2026, 1, 2), 1.0),
    ]
    rows[1]["transaction_date"] = "2026-01-02T00:00:00+07:00"

    with pytest.raises(
        InventoryDemandForecastError,
        match="valid timezone-free dates",
    ):
        forecast_inventory_demand(_frame(rows), date(2026, 6, 29))
