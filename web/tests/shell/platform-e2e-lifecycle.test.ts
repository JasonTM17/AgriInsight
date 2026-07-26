import { spawnSync } from "node:child_process";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const repoRoot = resolve(import.meta.dirname, "../../..");
const runner = resolve(repoRoot, "scripts/run-web-e2e-tests.ps1");

describe("real-platform E2E lifecycle behavior", () => {
  it("continues cleanup and releases an owned process under PowerShell 5.1", () => {
    const result = runPowerShell([
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      runner,
      "-RunLifecycleProbe"
    ]);
    expect(result.status, result.output).toBe(0);
    expect(result.output).toContain(
      "LIFECYCLE_PROBE=PASS cleanup_continued=true owned_process_stopped=true"
    );
  });

  it("rejects a concurrent runner without printing the global pass marker", () => {
    const escapedRunner = runner.replaceAll("'", "''");
    const script = `
$mutex = [Threading.Mutex]::new($false, "Global\\AgriInsight.WebE2E.Runner")
$held = $false
try {
    try {
        $held = $mutex.WaitOne(0)
    } catch [Threading.AbandonedMutexException] {
        $held = $true
    }
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File '${escapedRunner}' -SkipStaticGates 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) { throw "Concurrent runner unexpectedly succeeded" }
    if (($output -join [Environment]::NewLine) -notmatch "already owns the workspace") {
        throw "Concurrent runner did not report mutex ownership"
    }
    if (($output -join [Environment]::NewLine) -match "WEB_PLATFORM_E2E=PASS") {
        throw "Rejected runner printed the global pass marker"
    }
    Write-Output "CONCURRENT_RUNNER_PROBE=PASS"
} finally {
    if ($held) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}`;
    const encoded = Buffer.from(script, "utf16le").toString("base64");
    const result = runPowerShell([
      "-NoProfile",
      "-EncodedCommand",
      encoded
    ]);
    expect(result.status, result.output).toBe(0);
    expect(result.output).toContain("CONCURRENT_RUNNER_PROBE=PASS");
  });
});

function runPowerShell(arguments_: string[]) {
  const result = spawnSync("powershell.exe", arguments_, {
    cwd: repoRoot,
    encoding: "utf8",
    timeout: 30_000
  });
  return {
    output: `${result.stdout ?? ""}${result.stderr ?? ""}`,
    status: result.status
  };
}
