from __future__ import annotations

import hashlib
import re
from pathlib import Path


def test_field_ledger_gif_is_a_small_documented_loop() -> None:
    root = Path(__file__).parents[1]
    gif = root / "assets" / "generated" / "agriinsight-field-ledger-loop.gif"
    readme = (root / "assets" / "generated" / "README.md").read_text(encoding="utf-8")
    content = gif.read_bytes()

    assert content[:6] in {b"GIF87a", b"GIF89a"}
    assert len(content) < 1_000_000
    assert "960 × 480" in readme
    assert hashlib.sha256(content).hexdigest() in readme


def test_inventory_forecast_gif_is_a_small_documented_loop() -> None:
    root = Path(__file__).parents[1]
    gif = root / "assets" / "generated" / "agriinsight-inventory-forecast-loop.gif"
    readme = (root / "assets" / "generated" / "README.md").read_text(encoding="utf-8")
    content = gif.read_bytes()

    assert content[:6] in {b"GIF87a", b"GIF89a"}
    assert int.from_bytes(content[6:8], "little") == 960
    assert int.from_bytes(content[8:10], "little") == 600
    assert content.count(b"\x21\xf9\x04") == 3
    assert len(content) < 1_000_000
    assert "30504951460" in readme
    assert hashlib.sha256(content).hexdigest() in readme


def test_inventory_forecast_still_is_a_small_documented_webp() -> None:
    root = Path(__file__).parents[1]
    still = (
        root
        / "docs"
        / "assets"
        / "screens"
        / "inventory-demand-forecast-desktop.webp"
    )
    readme = (root / "assets" / "generated" / "README.md").read_text(encoding="utf-8")
    content = still.read_bytes()

    assert content[:4] == b"RIFF"
    assert content[8:12] == b"WEBP"
    assert len(content) < 500_000
    assert "1280 × 800" in readme
    assert hashlib.sha256(content).hexdigest() in readme


def test_yield_forecast_gif_is_a_small_documented_loop() -> None:
    root = Path(__file__).parents[1]
    gif = root / "assets" / "generated" / "agriinsight-yield-forecast-loop.gif"
    readme = (root / "assets" / "generated" / "README.md").read_text(
        encoding="utf-8"
    )
    content = gif.read_bytes()

    assert content[:6] in {b"GIF87a", b"GIF89a"}
    assert int.from_bytes(content[6:8], "little") == 960
    assert int.from_bytes(content[8:10], "little") == 600
    assert content.count(b"\x21\xf9\x04") == 2
    assert len(content) < 1_000_000
    assert "30696001895" in readme
    assert hashlib.sha256(content).hexdigest() in readme


def test_product_tour_gifs_match_documented_dimensions_frames_and_hashes() -> None:
    root = Path(__file__).parents[1]
    readme = (root / "assets" / "generated" / "README.md").read_text(
        encoding="utf-8"
    )

    expected = {
        "agriinsight-product-tour-desktop.gif": (960, 600),
        "agriinsight-product-tour-mobile.gif": (390, 844),
    }
    for name, dimensions in expected.items():
        content = (root / "assets" / "generated" / name).read_bytes()
        assert content[:6] in {b"GIF87a", b"GIF89a"}
        assert int.from_bytes(content[6:8], "little") == dimensions[0]
        assert int.from_bytes(content[8:10], "little") == dimensions[1]
        assert content.count(b"\x21\xf9\x04") == 7
        assert len(content) < 2_000_000
        assert hashlib.sha256(content).hexdigest() in readme

    assert "scripts/build-portfolio-tour-gifs.ps1" in readme


def test_media_gallery_references_existing_assets_and_all_motion_previews() -> None:
    root = Path(__file__).parents[1]
    gallery_path = root / "docs" / "media-gallery.md"
    gallery = gallery_path.read_text(encoding="utf-8")
    sources = re.findall(r'<img src="([^"]+)"', gallery)

    assert len(sources) == 32
    for source in sources:
        assert (gallery_path.parent / source).resolve().is_file(), source

    assert {
        "agriinsight-field-ledger-loop.gif",
        "agriinsight-inventory-forecast-loop.gif",
        "agriinsight-product-tour-desktop.gif",
        "agriinsight-product-tour-mobile.gif",
        "agriinsight-yield-forecast-loop.gif",
    } <= {Path(source).name for source in sources}
