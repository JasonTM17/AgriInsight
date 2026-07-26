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
