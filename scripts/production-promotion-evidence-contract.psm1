Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

Import-Module (Join-Path $PSScriptRoot "production-promotion-evidence-schema-helpers.psm1") -Force

$script:RepositoryRunUrlPattern = '^https://github\.com/JasonTM17/AgriInsight/actions/runs/[1-9][0-9]*$'
$script:ImageDefinitions = [ordered]@{
    python = @{ repository = "agriinsight-python"; environment = "AGRIINSIGHT_PYTHON_IMAGE" }
    backend = @{ repository = "agriinsight-backend"; environment = "AGRIINSIGHT_BACKEND_IMAGE" }
    web = @{ repository = "agriinsight-web"; environment = "AGRIINSIGHT_WEB_IMAGE" }
    analytics_api = @{ repository = "agriinsight-analytics-api"; environment = "AGRIINSIGHT_ANALYTICS_API_IMAGE" }
}
$script:ApprovalNames = @(
    "oidc", "broker", "hosting", "deployment", "recovery",
    "audit_retention", "credential_rotation", "observability", "registry", "license"
)

function Get-ValidatedRelease {
    param([object] $Release, [string] $Path, [switch] $RequireProduction)

    if ($RequireProduction -and (Get-RequiredString -Object $Release -Name "environment" -Path "$Path.environment") -cne "production") {
        throw "$Path.environment must equal production."
    }
    $tag = Get-RequiredString -Object $Release -Name "tag" -Path "$Path.tag"
    Assert-Pattern -Value $tag -Pattern '^v\d+\.\d+\.\d+$' -Name "$Path.tag" -Description "a semantic version tag"
    $commit = Get-RequiredString -Object $Release -Name "commit" -Path "$Path.commit"
    Assert-Pattern -Value $commit -Pattern '^[a-f0-9]{40}$' -Name "$Path.commit" -Description "a 40-character lowercase commit SHA"
    $ciRunUrl = Get-RequiredString -Object $Release -Name "ci_run_url" -Path "$Path.ci_run_url"
    $publicationRunUrl = Get-RequiredString -Object $Release -Name "publication_run_url" -Path "$Path.publication_run_url"
    Assert-Pattern -Value $ciRunUrl -Pattern $script:RepositoryRunUrlPattern -Name "$Path.ci_run_url" -Description "an AgriInsight GitHub Actions run URL"
    Assert-Pattern -Value $publicationRunUrl -Pattern $script:RepositoryRunUrlPattern -Name "$Path.publication_run_url" -Description "an AgriInsight GitHub Actions run URL"
    return [PSCustomObject]@{
        Tag = $tag
        Commit = $commit
        CiRunUrl = $ciRunUrl
        PublicationRunUrl = $publicationRunUrl
    }
}

function Assert-FirstPartyImage {
    param([object] $Images, [string] $Kind, [string] $Path)

    $image = Get-RequiredString -Object $Images -Name $Kind -Path $Path
    Assert-Pattern -Value $image -Pattern '@sha256:[a-f0-9]{64}$' -Name $Path -Description "an immutable OCI digest reference"
    $repository = $script:ImageDefinitions[$Kind].repository
    $allowedRepositories = @("nguyenson1710/$repository", "ghcr.io/jasontm17/$repository")
    if ($image.Split("@", 2)[0] -cnotin $allowedRepositories) {
        throw "$Path must reference the approved first-party $Kind repository."
    }
    return $image
}

function Assert-SelectedImages {
    param([object] $Images)

    $selected = [ordered]@{}
    foreach ($kind in $script:ImageDefinitions.Keys) {
        $image = Assert-FirstPartyImage -Images $Images -Kind $kind -Path "images.$kind"
        $environmentName = $script:ImageDefinitions[$kind].environment
        $environmentImage = [Environment]::GetEnvironmentVariable($environmentName)
        if ([string]::IsNullOrWhiteSpace($environmentImage)) {
            throw "$environmentName is required."
        }
        Assert-Pattern -Value $environmentImage -Pattern '@sha256:[a-f0-9]{64}$' -Name $environmentName -Description "an immutable OCI digest reference"
        if ($environmentImage -cne $image) {
            throw "$environmentName does not match promotion evidence."
        }
        $selected[$kind] = $image
    }
    return [PSCustomObject] $selected
}

function Assert-Target {
    param([object] $Target)

    $dockerContext = Get-RequiredString -Object $Target -Name "docker_context" -Path "target.docker_context"
    Assert-Pattern -Value $dockerContext -Pattern '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$' -Name "target.docker_context" -Description "a Docker context name"
    $endpointSha256 = Get-RequiredString -Object $Target -Name "docker_endpoint_sha256" -Path "target.docker_endpoint_sha256"
    Assert-Pattern -Value $endpointSha256 -Pattern '^[a-f0-9]{64}$' -Name "target.docker_endpoint_sha256" -Description "a lowercase SHA-256 Docker endpoint fingerprint"
    $deploymentIdentity = Get-RequiredString -Object $Target -Name "deployment_identity" -Path "target.deployment_identity"
    if ($deploymentIdentity -cne "agriinsight-release") {
        throw "target.deployment_identity must equal agriinsight-release."
    }
    $selectedContext = [Environment]::GetEnvironmentVariable("AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT")
    if ([string]::IsNullOrWhiteSpace($selectedContext)) {
        throw "AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT is required."
    }
    if ($selectedContext -cne $dockerContext) {
        throw "AGRIINSIGHT_PRODUCTION_DOCKER_CONTEXT does not match promotion evidence."
    }
    return [PSCustomObject]@{
        DockerContext = $dockerContext
        DockerEndpointSha256 = $endpointSha256
        DeploymentIdentity = $deploymentIdentity
    }
}

function Assert-Rollback {
    param([object] $Rollback, [object] $Images, [object] $CurrentRelease)

    $strategy = Get-RequiredString -Object $Rollback -Name "strategy" -Path "rollback.strategy"
    if ($strategy -notin @("redeploy-previous-digest", "disable-exposure")) {
        throw "rollback.strategy must be an approved rollback strategy."
    }
    [void](Get-RequiredString -Object $Rollback -Name "authority" -Path "rollback.authority")
    $evidenceRef = Get-RequiredString -Object $Rollback -Name "evidence_ref" -Path "rollback.evidence_ref"
    Assert-HttpsReference -Value $evidenceRef -Name "rollback.evidence_ref"

    if ($strategy -eq "disable-exposure") {
        $reference = Get-RequiredString -Object $Rollback -Name "disable_exposure_ref" -Path "rollback.disable_exposure_ref"
        Assert-HttpsReference -Value $reference -Name "rollback.disable_exposure_ref"
        return [PSCustomObject]@{
            Strategy = $strategy
            PreviousRelease = $null
            PreviousImages = $null
            DisableExposureRef = $reference
        }
    }

    $previousRelease = Get-ValidatedRelease -Release (Get-RequiredObject -Object $Rollback -Name "previous_release" -Path "rollback.previous_release") -Path "rollback.previous_release" -RequireProduction
    if ($previousRelease.Tag -ceq $CurrentRelease.Tag -or $previousRelease.Commit -ceq $CurrentRelease.Commit) {
        throw "rollback.previous_release must identify an earlier release."
    }
    $previousImages = Get-RequiredObject -Object $Rollback -Name "previous_images" -Path "rollback.previous_images"
    $validatedPreviousImages = [ordered]@{}
    $hasDifference = $false
    foreach ($kind in $script:ImageDefinitions.Keys) {
        $previous = Assert-FirstPartyImage -Images $previousImages -Kind $kind -Path "rollback.previous_images.$kind"
        if ($previous -cne $Images.$kind) { $hasDifference = $true }
        $validatedPreviousImages[$kind] = $previous
    }
    if (-not $hasDifference) {
        throw "rollback.previous_images must differ from the selected release."
    }
    return [PSCustomObject]@{
        Strategy = $strategy
        PreviousRelease = $previousRelease
        PreviousImages = [PSCustomObject] $validatedPreviousImages
        DisableExposureRef = $null
    }
}

function Assert-Recovery {
    param([object] $Recovery)

    foreach ($field in @("rpo", "rto", "retention", "key_owner", "restore_owner")) {
        [void](Get-RequiredString -Object $Recovery -Name $field -Path "recovery.$field")
    }
    foreach ($field in @("encrypted_off_host_backup_ref", "timed_drill_ref")) {
        $reference = Get-RequiredString -Object $Recovery -Name $field -Path "recovery.$field"
        Assert-HttpsReference -Value $reference -Name "recovery.$field"
    }
}

function Assert-Approvals {
    param([object] $Approvals)

    $seenReferences = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $now = [DateTimeOffset]::UtcNow
    foreach ($name in $script:ApprovalNames) {
        $path = "approvals.$name"
        $approval = Get-RequiredObject -Object $Approvals -Name $name -Path $path
        if ((Get-RequiredString -Object $approval -Name "control" -Path "$path.control") -cne $name) {
            throw "$path.control must equal $name."
        }
        [void](Get-RequiredString -Object $approval -Name "owner" -Path "$path.owner")
        $reference = Get-RequiredString -Object $approval -Name "approval_ref" -Path "$path.approval_ref"
        Assert-HttpsReference -Value $reference -Name "$path.approval_ref"
        if (-not $seenReferences.Add($reference)) {
            throw "$path.approval_ref must be unique."
        }
        $approvedAt = ConvertTo-RequiredTimestamp -Value (Get-RequiredProperty -Object $approval -Name "approved_at_utc" -Path "$path.approved_at_utc") -Name "$path.approved_at_utc"
        $dueAt = ConvertTo-RequiredTimestamp -Value (Get-RequiredProperty -Object $approval -Name "due_at_utc" -Path "$path.due_at_utc") -Name "$path.due_at_utc"
        if ($approvedAt -gt $dueAt) { throw "$path.approved_at_utc must not be after $path.due_at_utc." }
        if ($approvedAt -gt $now) { throw "$path.approved_at_utc cannot be in the future." }
        if ($dueAt -lt $now) { throw "$path.due_at_utc has expired." }
        [void](Get-RequiredString -Object $approval -Name "unlock_criterion" -Path "$path.unlock_criterion")
    }
}

function Test-ProductionPromotionEvidence {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)] [string] $EvidenceFile)

    if (-not (Test-Path -LiteralPath $EvidenceFile -PathType Leaf)) {
        throw "EvidenceFile does not exist."
    }
    try {
        $rawManifest = Get-Content -LiteralPath $EvidenceFile -Raw
        $manifest = $rawManifest | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        throw "EvidenceFile must contain valid JSON."
    }
    Assert-ApprovalTimestampEncoding -Json $rawManifest
    if ((Get-RequiredProperty -Object $manifest -Name "format_version" -Path "format_version") -ne 2) {
        throw "format_version must equal 2."
    }

    $release = Get-ValidatedRelease -Release (Get-RequiredObject -Object $manifest -Name "release" -Path "release") -Path "release" -RequireProduction

    $images = Assert-SelectedImages -Images (Get-RequiredObject -Object $manifest -Name "images" -Path "images")
    $target = Assert-Target -Target (Get-RequiredObject -Object $manifest -Name "target" -Path "target")
    $rollback = Assert-Rollback -Rollback (Get-RequiredObject -Object $manifest -Name "rollback" -Path "rollback") -Images $images -CurrentRelease $release
    Assert-Recovery -Recovery (Get-RequiredObject -Object $manifest -Name "recovery" -Path "recovery")
    Assert-Approvals -Approvals (Get-RequiredObject -Object $manifest -Name "approvals" -Path "approvals")

    return [PSCustomObject]@{
        Release = $release
        Images = $images
        Target = $target
        Rollback = $rollback
    }
}

Export-ModuleMember -Function Test-ProductionPromotionEvidence
