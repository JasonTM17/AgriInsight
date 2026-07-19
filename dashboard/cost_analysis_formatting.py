from __future__ import annotations


def format_vnd(value: float | None) -> str:
    if value is None:
        return "—"
    absolute = abs(value)
    if absolute >= 1_000_000_000:
        return f"{value / 1_000_000_000:,.2f} tỷ ₫"
    if absolute >= 1_000_000:
        return f"{value / 1_000_000:,.2f} triệu ₫"
    return f"{value:,.0f} ₫"


def format_number(value: float | int) -> str:
    return f"{value:,.2f}".rstrip("0").rstrip(".")


__all__ = ["format_number", "format_vnd"]
