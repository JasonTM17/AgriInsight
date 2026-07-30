from __future__ import annotations

import hashlib
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
