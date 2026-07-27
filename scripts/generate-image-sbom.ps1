[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Image,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

$trivy = Get-Command trivy -ErrorAction SilentlyContinue
if ($null -eq $trivy) {
    throw "Trivy is required to generate a CycloneDX image SBOM."
}

$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = [IO.Path]::GetDirectoryName($resolvedOutput)
if ([string]::IsNullOrWhiteSpace($outputDirectory)) {
    throw "SBOM output path must include a parent directory."
}
[IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

& $trivy.Source image `
    --format cyclonedx `
    --output $resolvedOutput `
    --scanners vuln `
    $Image
if ($LASTEXITCODE -ne 0) {
    throw "Trivy SBOM generation failed with exit code $LASTEXITCODE"
}
if (-not (Test-Path -LiteralPath $resolvedOutput -PathType Leaf)) {
    throw "Trivy did not create the expected SBOM: $resolvedOutput"
}

Write-Output "IMAGE_SBOM image=$Image format=cyclonedx output=$resolvedOutput status=PASS"
