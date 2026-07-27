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
});
