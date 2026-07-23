from __future__ import annotations

import hashlib
import tomllib
from pathlib import Path

from dashboard.page_visuals import PAGE_VISUALS, render_page_visual


EXPECTED_HASHES = {
    "overview-fields.webp": "9b03711e10447f443e4045bb9f47ed0df59672ce2b840f6049a18763a6b4ee3f",
    "farm-performance.webp": "d81a0f16bfcfc19b20b8ce82ca9ec2a9118b62ed9199ff2089755b2a8cfcb41e",
    "inventory-control.webp": "d9d5138b40c0a2606de13fb21e6f177c0b7b254ba6e1a2ccce2a58354663604a",
    "crop-health-evidence.webp": "abb6da1a4315537e121ffa6a2f4f25db860bb9afe8d02db48f63c61c04d29f8e",
    "data-quality-sensors.webp": "5adb169fc0b4814171676b5b203fadc5b8c1011a7abf8a8428d0e4df2db5ff5b",
    "cost-procurement.webp": "44675bb309251d9f8dd70d98b100613cea4c24a39f446323fde95c4bf25dbaf7",
}

SUPPLEMENTARY_EXPECTED_HASHES = {
    "work-operations.webp": "fb588ca0449aa64f6a0e25f99c19eb270e1a288656172b90ffa9a042d5f1ca19",
    "tenant-administration.webp": "a7b14aaeca7d7b13ac8ee95c6d512399ecd5250a14d9a4b13d297b2ff3036c0c",
}


class MissingAssetUi:
    def __init__(self) -> None:
        self.messages: list[str] = []

    def info(self, body: str) -> None:
        self.messages.append(body)


def test_visual_catalog_covers_every_dashboard_page() -> None:
    assert set(PAGE_VISUALS) == {
        "Executive",
        "Farm Performance",
        "Inventory",
        "Crop Health",
        "Data Quality",
        "Cost Analysis",
    }
    assert PAGE_VISUALS["Crop Health"].demo_evidence is True
    assert all(visual.alt_description for visual in PAGE_VISUALS.values())


def test_generated_assets_match_reviewed_hashes_and_budget() -> None:
    asset_root = Path(__file__).parents[1] / "dashboard" / "assets" / "generated"

    assert {visual.filename for visual in PAGE_VISUALS.values()} == set(EXPECTED_HASHES)
    reviewed_hashes = EXPECTED_HASHES | SUPPLEMENTARY_EXPECTED_HASHES
    for filename, expected_hash in reviewed_hashes.items():
        content = (asset_root / filename).read_bytes()
        assert len(content) <= 350 * 1024
        assert content[:4] == b"RIFF"
        assert content[8:12] == b"WEBP"
        assert hashlib.sha256(content).hexdigest() == expected_hash


def test_missing_visual_is_non_blocking_and_does_not_leak_path(tmp_path: Path) -> None:
    ui = MissingAssetUi()

    rendered = render_page_visual("Executive", asset_root=tmp_path, ui=ui)

    assert rendered is False
    assert ui.messages == [
        "Ảnh ngữ cảnh tạm thời không khả dụng; dữ liệu và biểu đồ vẫn hoạt động."
    ]
    assert str(tmp_path) not in ui.messages[0]


def test_streamlit_theme_uses_field_ledger_tokens() -> None:
    config_path = Path(__file__).parents[1] / ".streamlit" / "config.toml"
    theme = tomllib.loads(config_path.read_text(encoding="utf-8"))["theme"]

    assert theme["base"] == "light"
    assert theme["primaryColor"] == "#15803D"
    assert theme["backgroundColor"] == "#F7FAF8"
    assert theme["textColor"] == "#14261A"
    assert theme["showWidgetBorder"] is True
