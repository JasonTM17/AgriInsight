[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $EvidenceFile
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = "Stop"

Import-Module (Join-Path $PSScriptRoot "production-promotion-evidence-contract.psm1") -Force

$evidence = Test-ProductionPromotionEvidence -EvidenceFile $EvidenceFile
$imageCount = @($evidence.Images.PSObject.Properties).Count
Write-Output "PRODUCTION_PROMOTION_EVIDENCE status=PASS release=$($evidence.Release.Tag) commit=$($evidence.Release.Commit) image_count=$imageCount"
