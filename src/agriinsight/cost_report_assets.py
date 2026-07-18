from __future__ import annotations

from pathlib import Path


_REPORT_ASSETS_ROOT = Path(__file__).resolve().parent / "report-assets"


def bundled_font_dir() -> Path:
    """Return the wheel-safe directory containing the licensed PDF fonts."""

    return _REPORT_ASSETS_ROOT / "fonts"


def bundled_xlsx_builder() -> Path:
    """Return the wheel-safe artifact-tool workbook builder path."""

    return _REPORT_ASSETS_ROOT / "build-cost-report.mjs"


__all__ = ["bundled_font_dir", "bundled_xlsx_builder"]
