from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import streamlit as st


_ASSET_ROOT = Path(__file__).resolve().parent / "assets" / "generated"


@dataclass(frozen=True)
class PageVisual:
    filename: str
    title: str
    description: str
    alt_description: str
    demo_evidence: bool = False

    def path(self, asset_root: Path = _ASSET_ROOT) -> Path:
        return asset_root / self.filename


PAGE_VISUALS = {
    "Executive": PageVisual(
        "overview-fields.webp",
        "Toàn cảnh vận hành theo dữ liệu",
        "Đồng bộ hoạt động đồng ruộng, kho, tài chính, IoT và chất lượng dữ liệu.",
        "Cánh đồng lúa và rau nhìn từ trên cao, có kênh tưới, cảm biến, máy kéo nhỏ và nhà kính lúc bình minh.",
    ),
    "Farm Performance": PageVisual(
        "farm-performance.webp",
        "Từ chỉ số đến hiện trường",
        "So sánh trang trại rồi drill-down về khu vực, mùa vụ và bằng chứng vận hành.",
        "Quản lý trang trại kiểm tra máy tính bảng bên ruộng lúa và kênh tưới, phía xa có một nhân viên đồng ruộng.",
    ),
    "Inventory": PageVisual(
        "inventory-control.webp",
        "Kho có phạm vi và truy vết lô",
        "Ưu tiên FEFO, số dư chính xác và luồng nhập–xuất có thể kiểm toán.",
        "Hai nhân viên kiểm tra vật tư nông nghiệp niêm kín và phụ kiện tưới trong kho được sắp xếp theo lối đi.",
    ),
    "Crop Health": PageVisual(
        "crop-health-evidence.webp",
        "Bằng chứng quan sát cho bản trình diễn",
        "Hình ảnh chỉ minh họa cách UI gắn bằng chứng với chỉ số; không phải chẩn đoán thực địa.",
        "Một số lá lúa có đốm nâu cục bộ cạnh cảm biến nhỏ sau mưa, phần lớn cây xung quanh vẫn xanh khỏe.",
        demo_evidence=True,
    ),
    "Data Quality": PageVisual(
        "data-quality-sensors.webp",
        "Chất lượng bắt đầu từ điểm thu thập",
        "Theo dõi tính đầy đủ, hợp lệ, duy nhất, freshness và hành động remediation.",
        "Kỹ thuật viên kiểm tra trạm thời tiết và hệ thống cảm biến đất cạnh các hàng cây trồng sau mưa.",
    ),
    "Cost Analysis": PageVisual(
        "cost-procurement.webp",
        "Chi phí có ngữ cảnh và không đếm trùng",
        "Tách chi phí vận hành khỏi mua hàng, giữ drill-down về nguồn phát sinh.",
        "Quản lý vận hành và nhà cung cấp kiểm tra rau quả thu hoạch cạnh cân sàn, thùng hàng và xe nâng tay.",
    ),
}


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
