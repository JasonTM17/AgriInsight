import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const repoRoot = resolve(import.meta.dirname, "../../..");
const runner = readFileSync(
  resolve(repoRoot, "scripts/run-web-e2e-tests.ps1"),
  "utf8"
);
const demoBootstrap = readFileSync(
  resolve(repoRoot, "scripts/bootstrap-demo-environment.ps1"),
  "utf8"
);
const adminDatabaseHelper = readFileSync(
  resolve(repoRoot, "web/tests/e2e/helpers/admin-governance-database.ts"),
  "utf8"
);
const adminLifecycle = readFileSync(
  resolve(repoRoot, "web/tests/e2e/admin-lifecycle-and-assignments.spec.ts"),
  "utf8"
);

describe("real-platform E2E runner", () => {
  it("keeps package caches on the workspace drive", () => {
    expect(runner).toContain(
      '$mavenRepositoryRoot = Join-Path $repositoryRoot "_tmp\\host-caches\\maven-repository"'
    );
    expect(runner).toContain(
      '"-Dmaven.repo.local=$mavenRepositoryRoot"'
    );
    expect(runner).toContain(
      '$env:npm_config_cache = Join-Path $artifactRuntimeRoot "npm-cache"'
    );
  });

  it("pins and restores UTF-8 at the PostgreSQL seed boundary", () => {
    expect(demoBootstrap).toContain(
      "$previousPostgresClientEncoding = $env:PGCLIENTENCODING"
    );
    expect(demoBootstrap).toContain('$env:PGCLIENTENCODING = "UTF8"');
    expect(demoBootstrap).toContain(
      "$env:PGCLIENTENCODING = $previousPostgresClientEncoding"
    );
  });

  it("owns the analytics runtime for the full browser-test lifecycle", () => {
    expect(runner).toContain("$analyticsProcess = $null");
    expect(runner).toContain("Assert-TcpPortAvailable 58082");
    expect(runner).toContain("AGRIINSIGHT_ANALYTICS_ARTIFACT_ROOT");
    expect(runner).toContain("AGRIINSIGHT_ANALYTICS_RECONCILIATION_REPORT");
    expect(runner).toContain("AGRIINSIGHT_ANALYTICS_SPRING_BASE_URL");
    expect(runner).toContain("agriinsight.analytics_api");
    expect(runner).toContain("Wait-OwnedHttpProcessReady");
    expect(runner).toContain(
      '"http://127.0.0.1:58082/health/ready"'
    );
    expect(runner).toContain("Assert-ProcessOwnsTcpPort");
    expect(runner).toContain('Join-Path $env:JAVA_HOME "bin\\java.exe"');
    expect(runner).toContain('Join-Path $env:JAVA_HOME "bin/java"');
    expect(runner).toContain("-FilePath $javaExecutable");
    expect(runner).toContain("$LauncherProcess.Kill()");
    expect(runner).toContain("$LauncherProcess.Dispose()");
    expect(runner).toContain(
      '$runnerMutexName = "Global\\AgriInsight.WebE2E.Runner"'
    );
    expect(runner).toContain("$runnerMutex.WaitOne(0)");
    expect(runner).toContain("if ($runnerMutexHeld) {");

    const analyticsStart = runner.indexOf("ANALYTICS_HOST_START");
    const browserStart = runner.indexOf("PLAYWRIGHT_E2E_START");
    const finalCleanup = runner.lastIndexOf("} finally {");
    const analyticsStop = runner.indexOf(
      "Stop-OwnedProcess $analyticsProcess",
      finalCleanup
    );
    expect(analyticsStart).toBeGreaterThan(0);
    expect(browserStart).toBeGreaterThan(analyticsStart);
    expect(finalCleanup).toBeGreaterThan(browserStart);
    expect(analyticsStop).toBeGreaterThan(browserStart);
    expect(runner.slice(finalCleanup)).toContain("Invoke-CleanupStep");
    expect(runner.slice(finalCleanup)).toContain("Stop-OwnedProcess $backendProcess");

    const cleanupFailureGate = runner.lastIndexOf(
      'throw "Web E2E cleanup failed:'
    );
    const platformPass = runner.lastIndexOf("WEB_PLATFORM_E2E=PASS");
    expect(cleanupFailureGate).toBeGreaterThan(finalCleanup);
    expect(platformPass).toBeGreaterThan(cleanupFailureGate);
  });

  it("restarts the web runtime before the suite reaches the production login limit", () => {
    expect(runner).toContain('"--grep-invert", "@authorization|@work"');
    expect(runner).toContain('"--grep", "@authorization|@work"');
    expect(runner).not.toContain("AGRIINSIGHT_WEB_LOGIN_CLIENT_LIMIT");
  });

  it("resolves the opt-in media config from the repository root", () => {
    expect(runner).toContain('"web/playwright.capture.config.ts"');
    expect(runner).not.toContain(
      '"playwright", "test", "--config", "playwright.capture.config.ts"'
    );
  });

  it("selects only live activities and starts new assignments at version zero", () => {
    expect(adminDatabaseHelper).toContain(
      "activity.status IN ('PLANNED', 'STARTED')"
    );
    expect(adminLifecycle).toContain('.fill("0")');
  });

  it("uses runner.temp only behind the hosted CI guard", () => {
    expect(runner).toContain("[switch] $HostedCi");
    expect(runner).toContain('$env:CI -ne "true"');
    expect(runner).toContain("$env:RUNNER_TEMP");
    expect(runner).toContain("scripts/check-hosted-ci-disk.ps1");
    expect(runner).toContain(
      '$artifactRuntimeParent = Join-Path $env:RUNNER_TEMP "agriinsight-web-e2e"'
    );
    expect(runner).toContain("Invoke-WorkspaceDiskGuard");
  });

  it("accepts the supported Node 24 and npm 11 toolchain ranges", () => {
    expect(runner).toContain('$nodeVersion = [version]((& node --version).TrimStart("v"))');
    expect(runner).toContain(
      '$nodeVersion.Major -ne 24 -or $nodeVersion -lt [version]"24.12.0"'
    );
    expect(runner).toContain('$npmVersion = [version](& npm --version)');
    expect(runner).toContain(
      '$npmVersion.Major -ne 11 -or $npmVersion -lt [version]"11.6.2"'
    );
  });

  it("reclaims root-owned hosted bind mounts only after safe-path validation", () => {
    const cleanup = runner.slice(
      runner.indexOf("function Remove-SafeRuntimeDirectory"),
      runner.indexOf("function Assert-E2eProjectStopped")
    );
    const pathValidation = cleanup.indexOf("$resolvedPath.StartsWith");
    const hostedOwnership = cleanup.indexOf(
      "if ($HostedCi -and -not $isWindowsHost)"
    );
    const nativeRemoval = cleanup.indexOf(
      "Remove-Item -LiteralPath $resolvedPath -Recurse -Force"
    );

    expect(pathValidation).toBeGreaterThan(0);
    expect(hostedOwnership).toBeGreaterThan(pathValidation);
    expect(nativeRemoval).toBeGreaterThan(hostedOwnership);
    expect(cleanup).toContain('"chown", "--recursive", $runnerOwner, "--"');
  });
});
