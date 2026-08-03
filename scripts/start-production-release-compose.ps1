[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EvidenceFile,

    [ValidateSet("Validate", "Deploy", "Rollback")]
    [string] $Mode = "Validate",

    [switch] $ConfirmProductionChange
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

Import-Module (Join-Path $PSScriptRoot "production-promotion-evidence-contract.psm1") -Force

function Test-LastCommandSucceeded {
    param([bool] $Succeeded)

    if (-not $Succeeded) { return $false }
    $lastExitCode = Get-Variable -Name LASTEXITCODE -ValueOnly -ErrorAction SilentlyContinue
    return $null -eq $lastExitCode -or $lastExitCode -eq 0
}

function Invoke-ExternalCommand {
    param([string] $Command, [string[]] $Arguments, [string] $FailureMessage)

    & $Command @Arguments | Out-Null
    if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw $FailureMessage }
}

function Get-GitHubRun {
    param([string] $RunUrl, [string] $Purpose)

    if ($RunUrl -notmatch '^https://github\.com/JasonTM17/AgriInsight/actions/runs/([1-9][0-9]*)$') {
        throw "$Purpose run URL is not an AgriInsight Actions run."
    }
    $runId = $Matches[1]
    $rawRun = & gh api --hostname github.com "repos/JasonTM17/AgriInsight/actions/runs/$runId"
    if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not verify $Purpose workflow metadata." }
    return (($rawRun -join [Environment]::NewLine) | ConvertFrom-Json -ErrorAction Stop)
}

function Assert-GitHubRun {
    param([object] $Run, [object] $Release, [string] $WorkflowPath, [string] $ExpectedBranch, [string] $Purpose)

    $workflowPathPattern = "^$([regex]::Escape($WorkflowPath))(?:@.+)?$"
    if ($Run.path -cnotmatch $workflowPathPattern -or $Run.head_sha -cne $Release.Commit -or
        $Run.head_branch -cne $ExpectedBranch -or $Run.event -cne "push" -or
        $Run.status -cne "completed" -or $Run.conclusion -cne "success") {
        throw "$Purpose workflow metadata does not match the approved release."
    }
}

function Assert-ReleaseWorkflowEvidence {
    param([object] $Release)

    $ciRun = Get-GitHubRun -RunUrl $Release.CiRunUrl -Purpose "CI"
    Assert-GitHubRun -Run $ciRun -Release $Release -WorkflowPath ".github/workflows/ci.yml" -ExpectedBranch "main" -Purpose "CI"
    $publicationRun = Get-GitHubRun -RunUrl $Release.PublicationRunUrl -Purpose "publication"
    Assert-GitHubRun -Run $publicationRun -Release $Release -WorkflowPath ".github/workflows/publish-images.yml" -ExpectedBranch $Release.Tag -Purpose "publication"
    return [PSCustomObject]@{ Publication = $publicationRun }
}

function Get-WorkflowRunUpdatedAt {
    param([object] $Run, [string] $Purpose)

    $property = $Run.PSObject.Properties["updated_at"]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "$Purpose publication metadata has no updated_at timestamp."
    }
    try {
        if ($property.Value -is [DateTimeOffset]) { return $property.Value }
        if ($property.Value -is [DateTime]) { return [DateTimeOffset] $property.Value }
        return [DateTimeOffset]::Parse($property.Value, [System.Globalization.CultureInfo]::InvariantCulture, [System.Globalization.DateTimeStyles]::RoundtripKind)
    }
    catch {
        throw "$Purpose publication metadata has an invalid updated_at timestamp."
    }
}

function Assert-PreviousReleasePrecedesCurrent {
    param([object] $PreviousRelease, [object] $PreviousPublicationRun, [object] $CurrentRelease, [object] $CurrentPublicationRun)

    $previousVersion = [Version] $PreviousRelease.Tag.Substring(1)
    $currentVersion = [Version] $CurrentRelease.Tag.Substring(1)
    if ($previousVersion -ge $currentVersion) {
        throw "Rollback prior release is not earlier than the current release."
    }
    $previousUpdatedAt = Get-WorkflowRunUpdatedAt -Run $PreviousPublicationRun -Purpose "Prior"
    $currentUpdatedAt = Get-WorkflowRunUpdatedAt -Run $CurrentPublicationRun -Purpose "Current"
    if ($previousUpdatedAt -ge $currentUpdatedAt) {
        throw "Rollback prior release was not published before the current release."
    }
}

function Assert-ImageLabels {
    param([string] $Image, [object] $Release)

    Invoke-ExternalCommand -Command "docker" -Arguments @("pull", $Image) -FailureMessage "Could not pull an approved release image."
    $rawLabels = & docker image inspect $Image --format '{{json .Config.Labels}}'
    if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not inspect an approved release image." }
    $labels = (($rawLabels -join [Environment]::NewLine) | ConvertFrom-Json -ErrorAction Stop)
    if ($labels.'org.opencontainers.image.source' -cne "https://github.com/JasonTM17/AgriInsight" -or
        $labels.'org.opencontainers.image.revision' -cne $Release.Commit -or
        $labels.'org.opencontainers.image.version' -cne $Release.Tag.Substring(1)) {
        throw "Release image OCI labels do not match the approved release."
    }
}

function Assert-ReleaseTagParity {
    param([string] $Image, [object] $Release)

    $parts = $Image.Split("@", 2)
    $expectedDigest = $parts[1]
    $repositoryName = $parts[0].Split("/")[-1]
    foreach ($repository in @("nguyenson1710/$repositoryName", "ghcr.io/jasontm17/$repositoryName")) {
        foreach ($tag in @($Release.Tag.Substring(1), "sha-$($Release.Commit)")) {
            $rawDigest = & docker buildx imagetools inspect "$($repository):$tag" --format '{{json .Manifest.Digest}}'
            if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not verify paired registry digest parity." }
            if (($rawDigest -join [Environment]::NewLine).Trim().Trim('"') -cne $expectedDigest) {
                throw "Paired registry tag does not resolve to the approved digest."
            }
        }
    }
}

function Assert-PublishedAttestations {
    param([string] $Image)

    foreach ($kind in @("Provenance", "SBOM")) {
        $rawAttestation = & docker buildx imagetools inspect $Image --format "{{json .$kind}}"
        if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not verify published image attestations." }
        $attestation = ($rawAttestation -join [Environment]::NewLine).Trim()
        if ([string]::IsNullOrWhiteSpace($attestation) -or $attestation -eq "null") {
            throw "Published image attestation is missing."
        }
    }
}

function Set-SelectedImageEnvironment {
    param([object] $Images)

    $environmentNames = [ordered]@{
        python = "AGRIINSIGHT_PYTHON_IMAGE"; backend = "AGRIINSIGHT_BACKEND_IMAGE"
        web = "AGRIINSIGHT_WEB_IMAGE"; analytics_api = "AGRIINSIGHT_ANALYTICS_API_IMAGE"
    }
    foreach ($kind in $environmentNames.Keys) {
        [Environment]::SetEnvironmentVariable($environmentNames[$kind], $Images.PSObject.Properties[$kind].Value)
    }
}

function Assert-ReleaseImages {
    param([object] $Images, [object] $Release)

    foreach ($image in $Images.PSObject.Properties.Value) {
        Assert-ImageLabels -Image $image -Release $Release
        Assert-ReleaseTagParity -Image $image -Release $Release
        Assert-PublishedAttestations -Image $image
    }
}

function Invoke-ComposeUpAndVerify {
    param([string[]] $ComposeArguments)

    Invoke-ExternalCommand -Command "docker" -Arguments ($ComposeArguments + @("up", "--detach", "--wait", "--wait-timeout", "180")) -FailureMessage "Production Compose deployment failed health verification."
    $runningServices = @(& docker @($ComposeArguments + @("ps", "--status", "running", "--services")))
    if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not verify production service health after the change." }
    foreach ($service in @("backend", "analytics", "web")) {
        if ($runningServices -notcontains $service) { throw "Production service health verification did not complete." }
    }
}

function Invoke-DisableExposure {
    param([string[]] $ComposeArguments)

    Invoke-ExternalCommand -Command "docker" -Arguments ($ComposeArguments + @("down")) -FailureMessage "Approved exposure disablement failed."
    $runningServices = @(& docker @($ComposeArguments + @("ps", "--status", "running", "--services")))
    if (-not (Test-LastCommandSucceeded -Succeeded $?)) { throw "Could not verify exposure disablement." }
    if ($runningServices.Count -ne 0) { throw "Exposure disablement left release services running." }
}

if ($Mode -in @("Deploy", "Rollback") -and -not $ConfirmProductionChange) {
    throw "Deploy and Rollback modes require -ConfirmProductionChange."
}
$evidence = Test-ProductionPromotionEvidence -EvidenceFile $EvidenceFile
foreach ($command in @("docker", "gh")) {
    if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command is required for production release validation."
    }
}

$currentWorkflowEvidence = Assert-ReleaseWorkflowEvidence -Release $evidence.Release
$activeRelease = $evidence.Release
$activeImages = $evidence.Images
if ($Mode -eq "Rollback" -and $evidence.Rollback.Strategy -eq "redeploy-previous-digest") {
    $previousWorkflowEvidence = Assert-ReleaseWorkflowEvidence -Release $evidence.Rollback.PreviousRelease
    Assert-PreviousReleasePrecedesCurrent -PreviousRelease $evidence.Rollback.PreviousRelease -PreviousPublicationRun $previousWorkflowEvidence.Publication -CurrentRelease $evidence.Release -CurrentPublicationRun $currentWorkflowEvidence.Publication
    $activeRelease = $evidence.Rollback.PreviousRelease
    $activeImages = $evidence.Rollback.PreviousImages
}

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    Set-SelectedImageEnvironment -Images $activeImages
    Assert-ReleaseImages -Images $activeImages -Release $activeRelease
    $composeArguments = @(
        "compose", "-f", "compose.yaml", "-f", "compose.backend.yaml",
        "-f", "deploy/compose.release-overlay.yaml", "--profile", "backend"
    )
    Invoke-ExternalCommand -Command "docker" -Arguments ($composeArguments + @("config", "--quiet")) -FailureMessage "Production Compose configuration is invalid."
    if ($Mode -eq "Deploy" -or ($Mode -eq "Rollback" -and $evidence.Rollback.Strategy -eq "redeploy-previous-digest")) {
        Invoke-ComposeUpAndVerify -ComposeArguments $composeArguments
    }
    elseif ($Mode -eq "Rollback") {
        Invoke-DisableExposure -ComposeArguments $composeArguments
    }
}
finally {
    Pop-Location
}

Write-Output "PRODUCTION_RELEASE_COMPOSE status=PASS mode=$Mode release=$($activeRelease.Tag) commit=$($activeRelease.Commit)"
