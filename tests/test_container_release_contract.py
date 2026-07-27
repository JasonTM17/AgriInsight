from pathlib import Path

import pytest


ROOT = Path(__file__).parents[1]


@pytest.mark.parametrize(
    ("dockerfile_name", "base_image", "runtime_port"),
    [
        (
            "web.Dockerfile",
            "node:24.18.0-bookworm-slim@sha256:",
            "EXPOSE 3100",
        ),
        (
            "analytics-api.Dockerfile",
            "python:3.13-slim@sha256:",
            "EXPOSE 8081",
        ),
    ],
)
def test_first_party_service_images_are_pinned_non_root_and_health_checked(
    dockerfile_name: str,
    base_image: str,
    runtime_port: str,
) -> None:
    dockerfile = (
        ROOT / "deploy" / "docker" / dockerfile_name
    ).read_text(encoding="utf-8")

    assert base_image in dockerfile
    assert "USER 10001:10001" in dockerfile
    assert runtime_port in dockerfile
    assert "HEALTHCHECK" in dockerfile
    assert "OCI_REVISION" in dockerfile
    assert "OCI_VERSION" in dockerfile
    assert "latest" not in dockerfile.lower()


@pytest.mark.parametrize(
    ("ignore_name", "required_allowlist"),
    [
        (
            "web.Dockerfile.dockerignore",
            (
                "!web/src/**",
                "!web/public/**",
                "!web/scripts/**",
                "!web/db/**",
                "!dashboard/assets/generated/**",
            ),
        ),
        (
            "analytics-api.Dockerfile.dockerignore",
            ("!pyproject.toml", "!README.md", "!src/**"),
        ),
    ],
)
def test_dockerfile_contexts_are_allowlisted_without_local_state(
    ignore_name: str,
    required_allowlist: tuple[str, ...],
) -> None:
    ignore = (
        ROOT / "deploy" / "docker" / ignore_name
    ).read_text(encoding="utf-8")

    assert ignore.startswith("**\n")
    for pattern in required_allowlist:
        assert pattern in ignore
    assert "!.env" not in ignore
    assert "!artifacts" not in ignore
    assert "!.git" not in ignore
    assert "!node_modules" not in ignore
    assert "!.next" not in ignore


def test_web_standalone_runtime_exposes_an_unauthenticated_liveness_route() -> None:
    dockerfile = (
        ROOT / "deploy" / "docker" / "web.Dockerfile"
    ).read_text(encoding="utf-8")
    next_config = (ROOT / "web" / "next.config.ts").read_text(encoding="utf-8")
    health_route = (
        ROOT / "web" / "src" / "app" / "api" / "health" / "live" / "route.ts"
    ).read_text(encoding="utf-8")

    assert "rm -rf /usr/local/lib/node_modules/npm" in dockerfile
    assert "/opt/yarn-v*" in dockerfile
    assert 'output: "standalone"' in next_config
    assert "outputFileTracingRoot: repositoryRoot" in next_config
    assert "export async function GET" in health_route
    assert '"alive"' in health_route


def test_images_do_not_claim_a_license_before_the_owner_selects_one() -> None:
    dockerfiles = (
        ROOT / "Dockerfile",
        ROOT / "backend" / "Dockerfile",
        ROOT / "deploy" / "docker" / "web.Dockerfile",
        ROOT / "deploy" / "docker" / "analytics-api.Dockerfile",
    )

    assert not list(ROOT.glob("LICENSE*"))
    for dockerfile in dockerfiles:
        assert "org.opencontainers.image.licenses" not in dockerfile.read_text(
            encoding="utf-8"
        )


def test_ci_builds_scans_and_smokes_all_four_images_without_push() -> None:
    workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(
        encoding="utf-8"
    )

    for name in ("python", "backend", "web", "analytics-api"):
        assert f"- name: {name}" in workflow
    assert "deploy/docker/web.Dockerfile" in workflow
    assert "deploy/docker/analytics-api.Dockerfile" in workflow
    assert "push: false" in workflow
    assert "Scan the image before any release can publish it" in workflow
    assert "scripts/smoke-image-digest.ps1" in workflow


def test_release_publication_is_protected_serial_and_pre_scanned() -> None:
    workflow = (
        ROOT / ".github" / "workflows" / "publish-images.yml"
    ).read_text(encoding="utf-8")

    assert "environment: release-images" in workflow
    assert "max-parallel: 1" in workflow
    for name in ("python", "backend", "web", "analytics-api"):
        assert f"- name: {name}" in workflow
    assert "${{ secrets.DOCKERHUB_USERNAME }}" in workflow
    assert "${{ secrets.DOCKERHUB_TOKEN }}" in workflow
    assert "${{ secrets.GHCR_TOKEN || secrets.GITHUB_TOKEN }}" in workflow
    assert "provenance: mode=max" in workflow
    assert "sbom: true" in workflow
    assert "Scan the exact published digest" in workflow
    assert "Pull the exact published digest before smoke test" in workflow
    assert (
        'docker pull "${{ steps.refs.outputs.ghcr }}@${{ steps.build.outputs.digest }}"'
        in workflow
    )
    assert "type=raw,value=latest" not in workflow.lower()
    assert ":latest" not in workflow.lower()

    pre_scan = workflow.index(
        "- name: Scan the candidate before registry authentication"
    )
    docker_hub_login = workflow.index("- name: Authenticate to Docker Hub")
    publish = workflow.index("- name: Build, attest, and publish")
    pull_published_digest = workflow.index(
        "- name: Pull the exact published digest before smoke test"
    )
    smoke_published_digest = workflow.index(
        "- name: Smoke-test the exact published digest"
    )
    assert pre_scan < docker_hub_login < publish
    assert publish < pull_published_digest < smoke_published_digest


def test_release_helpers_enforce_read_only_non_root_smoke_and_sbom() -> None:
    smoke = (ROOT / "scripts" / "smoke-image-digest.ps1").read_text(
        encoding="utf-8"
    )
    sbom = (ROOT / "scripts" / "generate-image-sbom.ps1").read_text(
        encoding="utf-8"
    )

    assert "--read-only" in smoke
    assert "--cap-drop" in smoke
    assert "no-new-privileges" in smoke
    assert "Config.User" in smoke
    assert "/api/health/live" in smoke
    assert "/health/live" in smoke
    assert "--format" in sbom
    assert "cyclonedx" in sbom.lower()


def test_release_overlay_uses_immutable_images_and_hardened_runtime_defaults() -> None:
    compose = (
        ROOT / "deploy" / "compose.release-overlay.yaml"
    ).read_text(encoding="utf-8")

    for variable in (
        "AGRIINSIGHT_PYTHON_IMAGE",
        "AGRIINSIGHT_BACKEND_IMAGE",
        "AGRIINSIGHT_WEB_IMAGE",
        "AGRIINSIGHT_ANALYTICS_API_IMAGE",
    ):
        assert f"${{{variable}:?" in compose
    assert compose.count("read_only: true") >= 6
    assert compose.count('cap_drop: ["ALL"]') >= 6
    assert compose.count("no-new-privileges:true") >= 6
    assert "web-role-bootstrap:" in compose
    assert "web-migrate:" in compose
    assert "condition: service_healthy" in compose
    assert "/api/health/live" in compose
    assert "/health/ready" in compose
    assert (
        "AGRIINSIGHT_ASSISTANT_ENABLED: "
        "${AGRIINSIGHT_ASSISTANT_ENABLED:-false}"
    ) in compose
    assert "AGRIINSIGHT_LLM_API_KEY: ${AGRIINSIGHT_LLM_API_KEY:-}" in compose


def test_demo_overlay_orders_real_oidc_big_data_seed_and_reconciliation() -> None:
    compose = (
        ROOT / "deploy" / "compose.web-demo-overlay.yaml"
    ).read_text(encoding="utf-8")
    upstream = (
        (ROOT / "compose.backend.yaml").read_text(encoding="utf-8")
        + (ROOT / "compose.web-e2e.yaml").read_text(encoding="utf-8")
    )

    for service in (
        "oidc-configure",
        "demo-bundle",
        "demo-seed",
        "demo-reconcile",
        "analytics",
        "web",
    ):
        assert f"{service}:" in compose
    assert "--profile" in compose and "big-data" in compose
    assert "--confirm-local-demo" in compose
    assert "demo_tenant_reconciliation" in compose
    assert "${AGRIINSIGHT_DEMO_OIDC_CLIENT_SECRET:?" in compose
    assert "condition: service_completed_successfully" in compose
    assert "postgres:18.0-alpine@sha256:" in upstream
    assert "quay.io/keycloak/keycloak:26.7.0@sha256:" in upstream
    assert "agriinsight-postgres" not in compose
    assert "agriinsight-keycloak" not in compose
