from __future__ import annotations

import hashlib
import json
import re
import struct
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
EXPECTED_STEMS = {
    "overview-dashboard-desktop",
    "overview-dashboard-mobile",
    "work-operations-desktop",
    "work-operations-mobile",
    "cost-analysis-desktop",
    "cost-analysis-mobile",
    "crop-health-desktop",
    "crop-health-mobile",
    "data-quality-desktop",
    "data-quality-mobile",
    "assistant-evidence-first-desktop",
    "assistant-evidence-first-mobile",
    "tenant-administration-desktop",
    "tenant-administration-mobile",
}
EXPECTED_MEDIA = {
    "overview-dashboard": ("Overview", "/overview", "executive"),
    "work-operations": ("Work", "/work", "field-worker"),
    "cost-analysis": ("Cost Analysis", "/costs?lens=procurement", "executive"),
    "crop-health": ("Crop Health", "/crop-health", "analyst"),
    "data-quality": ("Data Quality", "/data-quality", "analyst"),
    "assistant-evidence-first": ("Assistant", "/assistant", "executive"),
    "tenant-administration": (
        "Administration",
        "/admin?search=tenant-admin&status=active",
        "tenant-admin",
    ),
}
EXPECTED_DIMENSIONS = {
    "desktop": (1280, 800),
    "mobile": (780, 1688),
}


def _read_webp_dimensions(path: Path) -> tuple[int, int]:
    payload = path.read_bytes()
    assert payload[:4] == b"RIFF"
    assert payload[8:12] == b"WEBP"

    offset = 12
    while offset + 8 <= len(payload):
        chunk_type = payload[offset : offset + 4]
        chunk_size = struct.unpack_from("<I", payload, offset + 4)[0]
        chunk = payload[offset + 8 : offset + 8 + chunk_size]

        if chunk_type == b"VP8X":
            width = int.from_bytes(chunk[4:7], "little") + 1
            height = int.from_bytes(chunk[7:10], "little") + 1
            return width, height
        if chunk_type == b"VP8 ":
            assert chunk[3:6] == b"\x9d\x01\x2a"
            width, height = struct.unpack_from("<HH", chunk, 6)
            return width & 0x3FFF, height & 0x3FFF
        if chunk_type == b"VP8L":
            assert chunk[0] == 0x2F
            dimensions = int.from_bytes(chunk[1:5], "little")
            width = (dimensions & 0x3FFF) + 1
            height = ((dimensions >> 14) & 0x3FFF) + 1
            return width, height

        offset += 8 + chunk_size + (chunk_size % 2)

    raise AssertionError(f"No WebP image chunk found in {path}")


def test_portfolio_capture_contract_is_complete() -> None:
    capture = (
        REPOSITORY_ROOT / "web/tests/capture/portfolio-media.spec.ts"
    ).read_text(encoding="utf-8")
    base_names = set(re.findall(r'name: "([a-z-]+)"', capture))
    expanded = {f"{name}-{viewport}" for name in base_names for viewport in ("desktop", "mobile")}

    assert expanded == EXPECTED_STEMS
    assert 'loginWithRealOidc(page, "executive"' in capture
    assert 'loginWithRealOidc(page, "field-worker"' in capture
    assert 'loginWithRealOidc(page, "analyst"' in capture
    assert 'loginWithRealOidc(page, "tenant-admin"' in capture
    assert 'route: "/costs?lens=procurement"' in capture
    assert 'text: "FASTAPI GOLD SNAPSHOT"' in capture
    assert "queryAssistant" not in capture
    assert "fullPage: false" in capture
    assert "fullPage: true" not in capture
    assert "hasContainingHorizontalClip" in capture


def test_media_builder_and_ci_publish_the_same_contract() -> None:
    builder = (REPOSITORY_ROOT / "scripts/build-demo-media.ps1").read_text(
        encoding="utf-8"
    )
    workflow = (REPOSITORY_ROOT / ".github/workflows/ci.yml").read_text(
        encoding="utf-8"
    )

    for stem in EXPECTED_STEMS:
        assert f'{stem}.png' in builder
        assert f'{stem}.png' in workflow
        assert f'{stem}.webp' in workflow
    assert "docs/assets/screens/catalog.json" in workflow
    assert "if-no-files-found: error" in workflow
    assert "Remove-Item -LiteralPath $candidate -Force" in builder
    cleanup = builder.split("foreach ($spec in $portfolioScreens)", 1)[1].split(
        "if (Test-Path -LiteralPath $portfolioManifestOut)", 1
    )[0]
    assert "$screensIn" not in cleanup
    assert 'route = "/costs?lens=procurement"' in builder
    assert 'kind = "hosted-product-screenshots"' in builder
    assert 'schemaVersion = 1' in builder


def test_hosted_portfolio_catalog_matches_committed_media() -> None:
    catalog_path = REPOSITORY_ROOT / "docs/assets/screens/catalog.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))

    assert catalog["schemaVersion"] == 1
    assert catalog["kind"] == "hosted-product-screenshots"
    assert catalog["capture"] == {
        "desktopViewport": "1440x900",
        "mobileViewport": "390x844",
        "deviceScaleFactor": 2,
    }

    provenance = catalog["provenance"]
    assert provenance["source"] == "github-actions"
    assert provenance["repository"] == "JasonTM17/AgriInsight"
    assert re.fullmatch(r"[0-9a-f]{40}", provenance["commitSha"])
    assert re.fullmatch(r"[1-9][0-9]*", provenance["runId"])
    assert provenance["runUrl"] == (
        "https://github.com/JasonTM17/AgriInsight/actions/runs/"
        f"{provenance['runId']}"
    )

    files = catalog["files"]
    assert len(files) == len(EXPECTED_STEMS)
    assert {Path(item["path"]).stem for item in files} == EXPECTED_STEMS

    for item in files:
        media_path = REPOSITORY_ROOT / item["path"]
        stem = media_path.stem
        viewport = stem.rsplit("-", 1)[1]
        base_name = stem.removesuffix(f"-{viewport}")
        area, route, persona = EXPECTED_MEDIA[base_name]
        payload = media_path.read_bytes()

        assert media_path.is_file()
        assert item["area"] == area
        assert item["route"] == route
        assert item["persona"] == persona
        assert item["viewport"] == viewport
        assert item["role"] == "hosted-product-webp"
        assert item["bytes"] == len(payload)
        assert item["sha256"] == hashlib.sha256(payload).hexdigest()
        assert (item["width"], item["height"]) == EXPECTED_DIMENSIONS[viewport]
        assert _read_webp_dimensions(media_path) == EXPECTED_DIMENSIONS[viewport]
        assert item["frameCount"] == 1
        assert "hosted integration stack" in item["evidenceBoundary"]
        assert "not live production telemetry" in item["evidenceBoundary"]
