[CmdletBinding(DefaultParameterSetName = 'Check')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Update')]
    [switch]$Update,

    [Parameter(ParameterSetName = 'Check')]
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$mavenRepo = Join-Path $repoRoot 'artifacts\_tmp\m2-repository'
$generated = Join-Path $backendRoot 'target\generated-contracts\agriinsight-api-v1.openapi.json'
$checkedIn = Join-Path $backendRoot 'src\main\resources\contracts\agriinsight-api-v1.openapi.json'

& (Join-Path $PSScriptRoot 'check-workspace-disk.ps1')
if ($LASTEXITCODE -ne 0) {
    throw 'Disk guard failed; OpenAPI export stopped.'
}

New-Item -ItemType Directory -Force -Path $mavenRepo | Out-Null
Push-Location $backendRoot
try {
    $arguments = @(
        '--batch-mode',
        '--no-transfer-progress',
        "-Dmaven.repo.local=$mavenRepo",
        '-Dtest=OpenApiArtifactExportTest',
        'test'
    )
    if ($Update) {
        $arguments = @('-Dagriinsight.openapi.skip-source-check=true') + $arguments
    }
    & '.\mvnw.cmd' @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "OpenAPI generation failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $generated -PathType Leaf)) {
    throw "Generated OpenAPI artifact is missing: $generated"
}

if ($Update) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $checkedIn) | Out-Null
    Copy-Item -LiteralPath $generated -Destination $checkedIn -Force
    Write-Output "OPENAPI_EXPORT mode=update artifact=$checkedIn"
} else {
    if (-not (Test-Path -LiteralPath $checkedIn -PathType Leaf)) {
        throw "Checked-in OpenAPI artifact is missing: $checkedIn"
    }
    $generatedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $generated).Hash
    $checkedInHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $checkedIn).Hash
    if ($generatedHash -ne $checkedInHash) {
        throw 'Checked-in OpenAPI artifact drifted; run this script with -Update and review the diff.'
    }
    Write-Output "OPENAPI_EXPORT mode=check sha256=$($generatedHash.ToLowerInvariant())"
}
