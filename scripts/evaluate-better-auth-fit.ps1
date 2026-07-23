[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$candidateVersion = "1.6.24"
$expectedIntegrity = "sha512-MtBxUKI2y5hXBBLU1MAXkUA/xTEPJ2lkzWLKCTQyG/IKNQQ8ve3tZK0uvq4AIv9iOLZHKUpBOBsDoqTEHaRb9Q=="
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $repositoryRoot "artifacts\_tmp\better-auth-fit"
$moduleRoot = Join-Path $runtimeRoot "node_modules"
$evaluator = Join-Path $repositoryRoot "web-auth-spike\evaluation\better-auth-refresh-race.mjs"
$env:npm_config_cache = Join-Path $repositoryRoot "artifacts\_tmp\npm-cache"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)] [string] $Command,
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $Failure
    )
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw $Failure }
}

& (Join-Path $PSScriptRoot "check-workspace-disk.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Disk guard failed; Better Auth fit evaluation stopped."
}

try {
    New-Item -ItemType Directory -Force -Path $runtimeRoot, $env:npm_config_cache | Out-Null
    $integrity = (& npm view "better-auth@$candidateVersion" "dist.integrity").Trim()
    if ($LASTEXITCODE -ne 0 -or $integrity -ne $expectedIntegrity) {
        throw "Better Auth package integrity does not match the reviewed candidate."
    }

    Invoke-Checked "npm" @(
        "install",
        "--prefix", $runtimeRoot,
        "--no-save",
        "--package-lock=false",
        "--ignore-scripts",
        "--no-audit",
        "--no-fund",
        "--silent",
        "better-auth@$candidateVersion"
    ) "Better Auth candidate installation failed"

    $env:BETTER_AUTH_NODE_MODULES = $moduleRoot
    Invoke-Checked "node" @($evaluator) "Better Auth refresh-fence evaluation failed"
} finally {
    Remove-Item Env:BETTER_AUTH_NODE_MODULES -ErrorAction SilentlyContinue
    $resolvedRuntimeRoot = [System.IO.Path]::GetFullPath($runtimeRoot)
    $expectedRuntimeRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot "artifacts\_tmp\better-auth-fit")
    )
    if ($resolvedRuntimeRoot -ne $expectedRuntimeRoot) {
        throw "Refusing to clean an unexpected Better Auth runtime path."
    }
    if (Test-Path -LiteralPath $resolvedRuntimeRoot) {
        Remove-Item -LiteralPath $resolvedRuntimeRoot -Recurse -Force
    }
}
