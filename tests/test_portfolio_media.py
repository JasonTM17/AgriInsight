from __future__ import annotations

import re
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
    assert "queryAssistant" not in capture


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
    assert 'kind = "hosted-product-screenshots"' in builder
    assert 'schemaVersion = 1' in builder
