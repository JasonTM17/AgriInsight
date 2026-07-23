[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$spikeRoot = Join-Path $repositoryRoot "web-auth-spike"
$cacheRoot = Join-Path $repositoryRoot "artifacts\_tmp\auth-spike"
$env:npm_config_cache = Join-Path $cacheRoot "npm-cache"
$env:TEMP = Join-Path $cacheRoot "temp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:npm_config_cache, $env:TEMP | Out-Null

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

Push-Location $repositoryRoot
try {
    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/check-workspace-disk.ps1"
    ) "Workspace disk guard failed"

    if ((& node --version) -ne "v24.12.0") {
        throw "Auth spike requires Node v24.12.0"
    }
    if ((& npm --version) -ne "11.6.2") {
        throw "Auth spike requires npm 11.6.2"
    }

    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/evaluate-better-auth-fit.ps1"
    ) "Better Auth candidate evaluation failed"
    Invoke-Checked "npm" @("--prefix", $spikeRoot, "ci", "--ignore-scripts") "npm ci failed"
    Invoke-Checked "npm" @("--prefix", $spikeRoot, "run", "typecheck") "Typecheck failed"
    Invoke-Checked "npm" @("--prefix", $spikeRoot, "test") "Unit tests failed"
    Invoke-Checked "npm" @("--prefix", $spikeRoot, "run", "build") "Next build failed"

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker info *> $null
        $dockerAvailable = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if (-not $dockerAvailable) {
        $env:AUTH_SPIKE_RUN_POSTGRES_TESTS = "0"
        Invoke-Checked "npm" @(
            "--prefix", $spikeRoot, "run", "test:integration"
        ) "Explicit integration skip run failed"
        Write-Warning "AUTH_SPIKE_REAL_ISSUER_GATE=NOT_PROVEN reason=docker_daemon_unavailable"
        exit 2
    }

    $required = @(
        "AUTH_SPIKE_POSTGRES_PASSWORD",
        "AUTH_SPIKE_DATABASE_URL",
        "AUTH_SPIKE_KEYCLOAK_ADMIN_USERNAME",
        "AUTH_SPIKE_KEYCLOAK_ADMIN_PASSWORD",
        "AUTH_SPIKE_OIDC_ISSUER",
        "AUTH_SPIKE_OIDC_CLIENT_ID",
        "AUTH_SPIKE_OIDC_CLIENT_SECRET",
        "AUTH_SPIKE_SESSION_ENCRYPTION_KEY_BASE64",
        "AUTH_SPIKE_TOKEN_KEY_ID",
        "AUTH_SPIKE_BASE_URL",
        "AUTH_SPIKE_CALLBACK_URL",
        "AUTH_SPIKE_ALLOWED_HOST",
        "AUTH_SPIKE_TENANT_ADMIN_PASSWORD",
        "AUTH_SPIKE_EXECUTIVE_PASSWORD",
        "AUTH_SPIKE_FARM_MANAGER_PASSWORD",
        "AUTH_SPIKE_INVENTORY_MANAGER_PASSWORD",
        "AUTH_SPIKE_ANALYST_PASSWORD",
        "AUTH_SPIKE_FIELD_WORKER_PASSWORD",
        "AUTH_SPIKE_SUPPLIER_PASSWORD",
        "AUTH_SPIKE_TEST_USERNAME",
        "AUTH_SPIKE_TEST_PASSWORD"
    )
    $missing = $required | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
    }
    if ($missing) {
        throw "Real issuer gate requires environment values for: $($missing -join ', ')"
    }

    $env:AUTH_SPIKE_RUN_POSTGRES_TESTS = "1"
    try {
        Invoke-Checked "docker" @(
            "compose", "-p", "agriinsight-auth-spike",
            "-f", "compose.auth-spike.yaml", "config", "--quiet"
        ) "Auth spike Compose validation failed"
        Invoke-Checked "docker" @(
            "compose", "-p", "agriinsight-auth-spike",
            "-f", "compose.auth-spike.yaml", "up", "-d", "--wait"
        ) "Auth spike services did not become healthy"
        Invoke-Checked "powershell" @(
            "-ExecutionPolicy", "Bypass", "-File", "scripts/configure-demo-oidc.ps1"
        ) "Demo OIDC configuration failed"
        Invoke-Checked "npm" @(
            "--prefix", $spikeRoot, "run", "test:integration"
        ) "PostgreSQL auth integration tests failed"
        Invoke-Checked "npm" @(
            "--prefix", $spikeRoot, "run", "test:e2e"
        ) "Real Keycloak/Chrome Playwright gate failed"
        Write-Output "AUTH_SPIKE_REAL_ISSUER_GATE=PROVEN adapter=openid-client"
    } finally {
        & docker compose -p agriinsight-auth-spike `
            -f compose.auth-spike.yaml down --remove-orphans
    }
} finally {
    Pop-Location
}
