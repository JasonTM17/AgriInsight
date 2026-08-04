[CmdletBinding()]
param(
    [string] $CatalogPath = "docs/assets/screens/catalog.json",
    [string] $DesktopOutput = "assets/generated/agriinsight-product-tour-desktop.gif",
    [string] $MobileOutput = "assets/generated/agriinsight-product-tour-mobile.gif",
    [ValidateRange(20, 1000)]
    [int] $DelayCentiseconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$catalogFile = Join-Path $repositoryRoot $CatalogPath
$screenRoot = Join-Path $repositoryRoot "docs/assets/screens"
$tourAreas = @(
    "overview-dashboard",
    "work-operations",
    "cost-analysis",
    "crop-health",
    "data-quality",
    "assistant-evidence-first",
    "tenant-administration"
)

function Resolve-Magick {
    if ($env:AGRIINSIGHT_MAGICK_PATH) {
        if (-not (Test-Path -LiteralPath $env:AGRIINSIGHT_MAGICK_PATH)) {
            throw "AGRIINSIGHT_MAGICK_PATH does not exist: $($env:AGRIINSIGHT_MAGICK_PATH)"
        }
        return $env:AGRIINSIGHT_MAGICK_PATH
    }

    $command = Get-Command "magick" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $isWindowsPlatform = (
        [System.Environment]::OSVersion.Platform -eq
        [System.PlatformID]::Win32NT
    )
    if (-not $isWindowsPlatform) {
        $command = Get-Command "convert" -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }

    throw "ImageMagick is required to build the portfolio tour GIFs."
}

function Resolve-ImageIdentify {
    param([string] $MagickPath)

    $command = Get-Command "identify" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $extension = [IO.Path]::GetExtension($MagickPath)
    $candidate = Join-Path (Split-Path -Parent $MagickPath) ("identify" + $extension)
    if (Test-Path -LiteralPath $candidate) { return $candidate }

    return $MagickPath
}

function Resolve-VerifiedFrames {
    param(
        [string] $Viewport,
        [object] $Catalog
    )

    $frames = foreach ($area in $tourAreas) {
        $name = "${area}-${Viewport}.webp"
        $path = Join-Path $screenRoot $name
        $entry = @($Catalog.files | Where-Object {
            [IO.Path]::GetFileName($_.path) -eq $name
        })

        if ($entry.Count -ne 1) {
            throw "Catalog must contain exactly one entry for $name."
        }
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing verified tour frame: $path"
        }

        $actualHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $entry[0].sha256) {
            throw "Catalog hash mismatch for $name."
        }
        Get-Item -LiteralPath $path
    }

    return @($frames)
}

function Build-TourGif {
    param(
        [System.IO.FileInfo[]] $Frames,
        [string] $OutputPath,
        [string] $Dimensions,
        [string] $Label,
        [string] $MagickPath,
        [string] $IdentifyPath
    )

    if ($Frames.Count -ne $tourAreas.Count) {
        throw "$Label requires $($tourAreas.Count) verified frames; found $($Frames.Count)."
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
    $framePaths = $Frames | ForEach-Object { $_.FullName }
    & $MagickPath -delay $DelayCentiseconds -loop 0 @framePaths `
        -resize "${Dimensions}!" -layers OptimizeTransparency -colors 192 `
        $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to build $Label."
    }

    $isMagickCommand = (
        [IO.Path]::GetFileNameWithoutExtension($IdentifyPath) -ieq "magick"
    )
    $renderedFrames = @(
        if ($isMagickCommand) {
            & $IdentifyPath identify $OutputPath
        } else {
            & $IdentifyPath $OutputPath
        }
    )
    if ($LASTEXITCODE -ne 0 -or $renderedFrames.Count -ne $Frames.Count) {
        throw "$Label frame count is invalid."
    }

    $output = Get-Item -LiteralPath $OutputPath
    if ($output.Length -gt 2MB) {
        throw "$Label exceeds the 2 MB repository limit."
    }

    $hash = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output (
        "PORTFOLIO_TOUR label={0} frames={1} bytes={2} sha256={3}" -f
        $Label, $Frames.Count, $output.Length, $hash
    )
}

if (-not (Test-Path -LiteralPath $catalogFile -PathType Leaf)) {
    throw "Hosted media catalog not found: $catalogFile"
}

$catalog = Get-Content -Raw -LiteralPath $catalogFile | ConvertFrom-Json
if ($catalog.kind -ne "hosted-product-screenshots") {
    throw "Unexpected media catalog kind: $($catalog.kind)"
}

$magick = Resolve-Magick
$imageIdentify = Resolve-ImageIdentify -MagickPath $magick
$desktopFrames = Resolve-VerifiedFrames -Viewport "desktop" -Catalog $catalog
$mobileFrames = Resolve-VerifiedFrames -Viewport "mobile" -Catalog $catalog

Build-TourGif -Frames $desktopFrames `
    -OutputPath (Join-Path $repositoryRoot $DesktopOutput) `
    -Dimensions "960x600" -Label "desktop" -MagickPath $magick `
    -IdentifyPath $imageIdentify
Build-TourGif -Frames $mobileFrames `
    -OutputPath (Join-Path $repositoryRoot $MobileOutput) `
    -Dimensions "390x844" -Label "mobile" -MagickPath $magick `
    -IdentifyPath $imageIdentify
