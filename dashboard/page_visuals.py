from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Protocol

import streamlit as st


_ASSET_ROOT = Path(__file__).resolve().parent / "assets" / "generated"
_CATALOG_PATH = _ASSET_ROOT / "catalog.json"


@dataclass(frozen=True)
class PageVisual:
    filename: str
    title: str
    description: str
    alt_description: str
    demo_evidence: bool = False

    def path(self, asset_root: Path = _ASSET_ROOT) -> Path:
        return asset_root / self.filename


_PAGE_TO_CATALOG_AREA = {
    "Executive": "overview",
    "Farm Performance": "farms",
    "Inventory": "inventory",
    "Crop Health": "crop-health",
    "Data Quality": "data-quality",
    "Cost Analysis": "costs",
}


def _load_page_visuals() -> dict[str, PageVisual]:
    catalog = json.loads(_CATALOG_PATH.read_text(encoding="utf-8"))
    entries = {entry["area"]: entry for entry in catalog["entries"]}
    return {
        page_name: PageVisual(
            entry["filename"],
            entry["title"],
            entry["description"],
            entry["alt"],
            demo_evidence=entry.get("demoEvidence", False),
        )
        for page_name, area in _PAGE_TO_CATALOG_AREA.items()
        for entry in [entries[area]]
    }


PAGE_VISUALS = _load_page_visuals()


class VisualUi(Protocol):
    def columns(self, spec: tuple[float, float], **kwargs): ...
    def container(self, **kwargs): ...
    def image(self, image, **kwargs): ...
    def info(self, body: str): ...
    def markdown(self, body: str): ...
    def caption(self, body: str): ...
    def warning(self, body: str, **kwargs): ...


def render_page_visual(
    page_name: str,
    *,
    asset_root: Path = _ASSET_ROOT,
    ui: VisualUi = st,
) -> bool:
    visual = PAGE_VISUALS[page_name]
    path = visual.path(asset_root)
    if not path.is_file():
        ui.info("Ảnh ngữ cảnh tạm thời không khả dụng; dữ liệu và biểu đồ vẫn hoạt động.")
        return False

    with ui.container(border=True):
        image_column, context_column = ui.columns((1.65, 1), gap="medium")
        with image_column:
            ui.image(str(path), caption=visual.alt_description, width="stretch")
        with context_column:
            ui.caption("BỐI CẢNH VẬN HÀNH")
            ui.markdown(f"**{visual.title}**")
            ui.caption(visual.description)
            if visual.demo_evidence:
                ui.warning(
                    "AI-generated demo evidence — không phải ảnh quan sát thực địa "
                    "và không dùng để chẩn đoán.",
                    icon="⚠️",
                )
            else:
                ui.caption("Ảnh minh họa được tạo riêng cho AgriInsight.")
    return True


__all__ = ["PAGE_VISUALS", "PageVisual", "render_page_visual"]
