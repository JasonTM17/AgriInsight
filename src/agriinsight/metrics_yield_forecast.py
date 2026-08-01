from __future__ import annotations

from datetime import date, timedelta
import sqlite3

import pandas as pd

from agriinsight.metrics_yield_forecast_contract import (
    ACTIVE_SEASON_COLUMNS,
    YIELD_FORECAST_GOLD_COLUMNS,
    YieldForecastGoldContractError,
    validate_yield_forecast_gold,
)
from agriinsight.yield_forecast import forecast_active_season_yield


class YieldForecastGoldError(ValueError):
    """Raised when warehouse facts cannot produce safe yield evidence."""


def active_yield_forecast_seasons(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> pd.DataFrame:
    """Return the canonical active season set eligible at the forecast as-of date."""

    _validate_as_of_date(as_of_date)
    return pd.read_sql_query(
        """
        SELECT f.farm_code,
               fi.field_code,
               s.season_code,
               c.crop_code,
               s.start_date AS season_start_date,
               s.expected_harvest_date,
               s.season_area_ha,
               s.target_yield_kg
        FROM dim_season s
        JOIN dim_farm f USING (farm_key)
        JOIN dim_field fi USING (field_key)
        JOIN dim_crop c USING (crop_key)
        WHERE s.status = 'active'
          AND date(s.start_date) <= date(?)
          AND date(s.expected_harvest_date) > date(?)
        ORDER BY f.farm_code, fi.field_code, s.season_code
        """,
        connection,
        params=(as_of_date.isoformat(), as_of_date.isoformat()),
    ).loc[:, ACTIVE_SEASON_COLUMNS]


def build_yield_forecast_gold(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> pd.DataFrame:
    """Materialize deterministic yield evidence from bounded warehouse facts."""

    _validate_as_of_date(as_of_date)
    _reject_season_field_farm_mismatch(connection, as_of_date)
    _reject_harvest_dimension_mismatch(connection, as_of_date)
    candidates = active_yield_forecast_seasons(connection, as_of_date)
    history = _completed_harvest_history(connection, as_of_date)
    try:
        forecast = forecast_active_season_yield(
            history,
            candidates.assign(season_status="active"),
            as_of_date,
        )
        forecast = _with_target_context(forecast, candidates)
        validate_yield_forecast_gold(forecast, as_of_date, candidates)
    except (TypeError, ValueError, YieldForecastGoldContractError) as error:
        raise YieldForecastGoldError(str(error)) from error
    return forecast


def _with_target_context(
    forecast: pd.DataFrame,
    candidates: pd.DataFrame,
) -> pd.DataFrame:
    targets = candidates.set_index("season_code")["target_yield_kg"]
    forecast = forecast.copy()
    forecast.insert(
        10,
        "target_yield_kg",
        forecast["season_code"].map(targets),
    )
    return forecast.loc[:, YIELD_FORECAST_GOLD_COLUMNS]


def _completed_harvest_history(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> pd.DataFrame:
    cutoff = (as_of_date + timedelta(days=1)).isoformat()
    return pd.read_sql_query(
        """
        SELECT h.harvest_id,
               h.harvested_at,
               f.farm_code,
               fi.field_code,
               s.season_code,
               c.crop_code,
               s.start_date AS season_start_date,
               s.completed_at AS season_completed_at,
               s.season_area_ha,
               s.status AS season_status,
               h.harvest_quantity_kg
        FROM fact_harvest h
        JOIN dim_season s USING (season_key)
        JOIN dim_farm f ON f.farm_key = h.farm_key
        JOIN dim_field fi ON fi.field_key = h.field_key
        JOIN dim_crop c ON c.crop_key = h.crop_key
        WHERE h.harvested_at < ?
          AND s.completed_at < ?
        ORDER BY s.season_code, h.harvested_at, h.harvest_id
        """,
        connection,
        params=(cutoff, cutoff),
    )


def _reject_harvest_dimension_mismatch(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> None:
    mismatch = connection.execute(
        """
        SELECT h.harvest_id
        FROM fact_harvest h
        JOIN dim_season s USING (season_key)
        WHERE h.harvested_at < ?
          AND (
              h.farm_key != s.farm_key
              OR h.field_key != s.field_key
              OR h.crop_key != s.crop_key
          )
        LIMIT 1
        """,
        ((as_of_date + timedelta(days=1)).isoformat(),),
    ).fetchone()
    if mismatch is not None:
        raise YieldForecastGoldError("harvest dimension relationship is invalid")


def _reject_season_field_farm_mismatch(
    connection: sqlite3.Connection,
    as_of_date: date,
) -> None:
    cutoff = (as_of_date + timedelta(days=1)).isoformat()
    mismatch = connection.execute(
        """
        SELECT s.season_key
        FROM dim_season s
        JOIN dim_field fi USING (field_key)
        WHERE s.farm_key != fi.farm_key
          AND (
              (
                  s.status = 'active'
                  AND date(s.start_date) <= date(?)
                  AND date(s.expected_harvest_date) > date(?)
              )
              OR EXISTS (
                  SELECT 1
                  FROM fact_harvest h
                  WHERE h.season_key = s.season_key
                    AND h.harvested_at < ?
                    AND s.completed_at < ?
              )
          )
        LIMIT 1
        """,
        (as_of_date.isoformat(), as_of_date.isoformat(), cutoff, cutoff),
    ).fetchone()
    if mismatch is not None:
        raise YieldForecastGoldError("season field and farm relationship is invalid")


def _validate_as_of_date(as_of_date: date) -> None:
    if type(as_of_date) is not date:
        raise YieldForecastGoldError("forecast as-of date must be a date")
