[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$ConfirmLocalDemo,
    [string]$ArtifactRoot = "artifacts/big-data",
    [string]$DatabaseHost = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 5432,
    [string]$DatabaseName = "agriinsight_demo",
    [string]$DatabaseUser = "agriinsight_migrator",
    [string]$OutputDirectory = "_tmp/demo-bootstrap"
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

if (-not $ConfirmLocalDemo) {
    throw "Explicit -ConfirmLocalDemo is required."
}
if ($DatabaseHost -notin @("127.0.0.1", "localhost", "::1")) {
    throw "Demo bootstrap only accepts a loopback database host."
}
if ($DatabaseName -ne "agriinsight_demo") {
    throw "Demo bootstrap only accepts the agriinsight_demo database."
}
if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD)) {
    throw "Set PGPASSWORD for the local demo migrator without writing it to disk."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$diskGuard = Join-Path $PSScriptRoot "check-workspace-disk.ps1"
& powershell -ExecutionPolicy Bypass -File $diskGuard
if ($LASTEXITCODE -ne 0) {
    throw "Workspace disk guard did not pass."
}

$resolvedArtifactRoot = [IO.Path]::GetFullPath(
    (Join-Path $repositoryRoot $ArtifactRoot)
)
$resolvedOutput = [IO.Path]::GetFullPath(
    (Join-Path $repositoryRoot $OutputDirectory)
)
$expectedOutputRoot = [IO.Path]::GetFullPath(
    (Join-Path $repositoryRoot "_tmp")
)
if (-not $resolvedOutput.StartsWith(
    $expectedOutputRoot + [IO.Path]::DirectorySeparatorChar,
    [StringComparison]::OrdinalIgnoreCase
)) {
    throw "OutputDirectory must stay under repository _tmp."
}

$contractPath = Join-Path $repositoryRoot "deploy/demo/demo-tenant.json"
$pythonPath = Join-Path $repositoryRoot "src"
$previousPythonPath = $env:PYTHONPATH
try {
    $env:PYTHONPATH = $pythonPath
    python -m agriinsight.demo_tenant_bootstrap `
        --artifact-root $resolvedArtifactRoot `
        --contract $contractPath `
        --output-directory $resolvedOutput `
        --confirm-local-demo
    if ($LASTEXITCODE -ne 0) {
        throw "Demo bundle generation failed."
    }

    $psqlCommand = Get-Command psql -ErrorAction Stop
    $connectionArguments = @(
        "-X",
        "--no-psqlrc",
        "--host", $DatabaseHost,
        "--port", "$DatabasePort",
        "--dbname", $DatabaseName,
        "--username", $DatabaseUser,
        "--set", "ON_ERROR_STOP=1"
    )
    $databaseMarker = & $psqlCommand.Source @connectionArguments `
        --quiet `
        --tuples-only `
        --no-align `
        --command "SELECT current_setting('app.agriinsight_demo_database', TRUE)"
    if ($LASTEXITCODE -ne 0 -or [string]($databaseMarker -join "") -ne "true") {
        throw "Target PostgreSQL is not marked as an AgriInsight local-demo server."
    }
    & $psqlCommand.Source @connectionArguments `
        --quiet `
        --file (Join-Path $resolvedOutput "seed.sql")
    if ($LASTEXITCODE -ne 0) {
        throw "Transactional demo seed failed."
    }

    $actualJson = & $psqlCommand.Source @connectionArguments `
        --quiet `
        --tuples-only `
        --no-align `
        --file (Join-Path $resolvedOutput "inspect.sql")
    if ($LASTEXITCODE -ne 0) {
        throw "Operational catalog inspection failed."
    }
    $actualPath = Join-Path $resolvedOutput "actual-catalog.json"
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText(
        $actualPath,
        [string]($actualJson -join "`n"),
        $utf8WithoutBom
    )

    python -m agriinsight.demo_tenant_reconciliation `
        --artifact-root $resolvedArtifactRoot `
        --contract $contractPath `
        --actual-json $actualPath `
        --output (Join-Path $resolvedOutput "reconciliation.json")
    if ($LASTEXITCODE -ne 0) {
        throw "Demo canonical reconciliation failed."
    }

    $bundle = Get-Content (Join-Path $resolvedOutput "bundle.json") -Raw |
        ConvertFrom-Json
    $reconciliation = Get-Content (
        Join-Path $resolvedOutput "reconciliation.json"
    ) -Raw | ConvertFrom-Json
    foreach ($property in @(
        "demoTenantId",
        "manifestFingerprint",
        "runId"
    )) {
        if ([string]$bundle.$property -ne [string]$reconciliation.$property) {
            throw (
                "Demo bundle and reconciliation disagree on {0}." -f `
                    $property
            )
        }
    }
}
finally {
    $env:PYTHONPATH = $previousPythonPath
}

Write-Output (
    "DEMO_BOOTSTRAP status=PASS database={0} report={1}" -f `
        $DatabaseName,
        (Join-Path $resolvedOutput "reconciliation.json")
)
