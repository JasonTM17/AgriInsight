from __future__ import annotations

import io

import pandas as pd

from agriinsight.cost_report_contract import PreparedCostReport


_FORMULA_PREFIXES = ("=", "+", "-", "@")


def escape_spreadsheet_text(value: object) -> object:
    if isinstance(value, str) and value.startswith(_FORMULA_PREFIXES):
        return f"'{value}"
    return value


def escaped_frame(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    for column in result.select_dtypes(include=("object", "string")).columns:
        result[column] = result[column].map(escape_spreadsheet_text)
    return result


def render_cost_report_csv(report: PreparedCostReport) -> bytes:
    buffer = io.StringIO(newline="")
    escaped_frame(report.csv_ledger).to_csv(
        buffer,
        index=False,
        lineterminator="\n",
        float_format="%.6f",
    )
    return buffer.getvalue().encode("utf-8-sig")
