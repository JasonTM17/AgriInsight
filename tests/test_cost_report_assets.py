from __future__ import annotations

from agriinsight.cost_report_assets import bundled_font_dir, bundled_xlsx_builder


def test_report_runtime_assets_are_packaged_with_agriinsight() -> None:
    font_dir = bundled_font_dir()

    assert (font_dir / "NotoSans-Regular.ttf").is_file()
    assert (font_dir / "NotoSans-Bold.ttf").is_file()
    assert (font_dir / "OFL.txt").is_file()
    assert bundled_xlsx_builder().is_file()
