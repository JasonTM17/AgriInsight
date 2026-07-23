from __future__ import annotations

import json
import math
from datetime import date, datetime
from typing import Any, Mapping

import pandas as pd


def camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.capitalize() for part in tail)


def json_safe(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, Mapping):
        return {camel(str(key)): json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [json_safe(item) for item in value]
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if hasattr(value, "item"):
        return json_safe(value.item())
    return value


def records(frame: pd.DataFrame) -> list[dict[str, Any]]:
    if frame.empty:
        return []
    normalized = frame.rename(columns={column: camel(column) for column in frame})
    payload = json.loads(normalized.to_json(orient="records", date_format="iso"))
    return json_safe(payload)


def first_record(frame: pd.DataFrame) -> dict[str, Any]:
    values = records(frame.head(1))
    return values[0] if values else {}
