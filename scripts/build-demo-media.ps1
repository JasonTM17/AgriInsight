[CmdletBinding()]
param(
    [string] $CaptureRoot = "artifacts/media-capture",
    [string] $OutputRoot = "docs/assets/screens",
    [string] $GifPath = "docs/assets/agriinsight-tour.gif",
    [string] $ForecastGifPath = "assets/generated/agriinsight-inventory-forecast-loop.gif",
    [string] $YieldForecastGifPath = "assets/generated/agriinsight-yield-forecast-loop.gif",
    [string] $YieldForecastManifestPath = "assets/generated/agriinsight-yield-forecast-media-manifest.json",
    [string] $PortfolioManifestPath = "docs/assets/screens/catalog.json",
    [int] $StillWidth = 1280,
    [ValidateRange(1, 16000)]
    [int] $StillMaxHeight = 12000,
    [int] $GifWidth = 960,
    [int] $GifDelayCentiseconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$captureRoot = Join-Path $repositoryRoot $CaptureRoot
$screensIn = Join-Path $captureRoot "screens"
$framesIn = Join-Path $captureRoot "frames"
$screensOut = Join-Path $repositoryRoot $OutputRoot
$gifOut = Join-Path $repositoryRoot $GifPath
$portfolioManifestOut = Join-Path $repositoryRoot $PortfolioManifestPath
$portfolioScreens = @(
    [ordered]@{ source = "overview-dashboard-desktop.png"; output = "overview-dashboard-desktop.webp"; area = "Overview"; route = "/overview"; persona = "executive"; viewport = "desktop" },
    [ordered]@{ source = "overview-dashboard-mobile.png"; output = "overview-dashboard-mobile.webp"; area = "Overview"; route = "/overview"; persona = "executive"; viewport = "mobile" },
    [ordered]@{ source = "work-operations-desktop.png"; output = "work-operations-desktop.webp"; area = "Work"; route = "/work"; persona = "field-worker"; viewport = "desktop" },
    [ordered]@{ source = "work-operations-mobile.png"; output = "work-operations-mobile.webp"; area = "Work"; route = "/work"; persona = "field-worker"; viewport = "mobile" },
    [ordered]@{ source = "cost-analysis-desktop.png"; output = "cost-analysis-desktop.webp"; area = "Cost Analysis"; route = "/costs?lens=procurement"; persona = "executive"; viewport = "desktop" },
    [ordered]@{ source = "cost-analysis-mobile.png"; output = "cost-analysis-mobile.webp"; area = "Cost Analysis"; route = "/costs?lens=procurement"; persona = "executive"; viewport = "mobile" },
    [ordered]@{ source = "crop-health-desktop.png"; output = "crop-health-desktop.webp"; area = "Crop Health"; route = "/crop-health"; persona = "analyst"; viewport = "desktop" },
    [ordered]@{ source = "crop-health-mobile.png"; output = "crop-health-mobile.webp"; area = "Crop Health"; route = "/crop-health"; persona = "analyst"; viewport = "mobile" },
    [ordered]@{ source = "data-quality-desktop.png"; output = "data-quality-desktop.webp"; area = "Data Quality"; route = "/data-quality"; persona = "analyst"; viewport = "desktop" },
    [ordered]@{ source = "data-quality-mobile.png"; output = "data-quality-mobile.webp"; area = "Data Quality"; route = "/data-quality"; persona = "analyst"; viewport = "mobile" },
    [ordered]@{ source = "assistant-evidence-first-desktop.png"; output = "assistant-evidence-first-desktop.webp"; area = "Assistant"; route = "/assistant"; persona = "executive"; viewport = "desktop" },
    [ordered]@{ source = "assistant-evidence-first-mobile.png"; output = "assistant-evidence-first-mobile.webp"; area = "Assistant"; route = "/assistant"; persona = "executive"; viewport = "mobile" },
    [ordered]@{ source = "tenant-administration-desktop.png"; output = "tenant-administration-desktop.webp"; area = "Administration"; route = "/admin?search=tenant-admin&status=active"; persona = "tenant-admin"; viewport = "desktop" },
    [ordered]@{ source = "tenant-administration-mobile.png"; output = "tenant-administration-mobile.webp"; area = "Administration"; route = "/admin?search=tenant-admin&status=active"; persona = "tenant-admin"; viewport = "mobile" }
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
    # Bounded probe of the default installer layout. A recursive sweep of
    # Program Files can take minutes, so only the one directory level is checked.
    foreach ($base in @("$env:ProgramFiles", "${env:ProgramFiles(x86)}")) {
        if (-not $base -or -not (Test-Path -LiteralPath $base)) { continue }
        $candidate = Get-ChildItem -LiteralPath $base -Directory `
            -Filter "ImageMagick*" -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName "magick.exe" } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    throw (
        "ImageMagick (magick or convert) is required to build documentation " +
        "media. Install it or set AGRIINSIGHT_MAGICK_PATH."
    )
}

function Build-Gif {
    param(
        [System.IO.FileInfo[]] $Frames,
        [string] $OutputPath,
        [string] $Label
    )
    $frameCount = @($Frames).Count
    if ($frameCount -lt 2) {
        throw "$Label needs at least two frames; found $frameCount"
    }
    New-Item -ItemType Directory -Force `
        -Path (Split-Path -Parent $OutputPath) | Out-Null
    $framePaths = $Frames | ForEach-Object { $_.FullName }
    & $magick -delay $GifDelayCentiseconds -loop 0 @framePaths `
        -resize "${GifWidth}x>" -layers OptimizeTransparency -colors 200 `
        $OutputPath
    if ($LASTEXITCODE -ne 0) { throw "Failed to assemble $OutputPath" }

    $gifMb = [math]::Round((Get-Item -LiteralPath $OutputPath).Length / 1MB, 2)
    Write-Output (
        "MEDIA_GIF label={0} frames={1} size={2} MB" -f
        $Label, $frameCount, $gifMb
    )
    if ($gifMb -gt 8) {
        throw "$Label is ${gifMb} MB, which is too heavy for a repository README"
    }
}

function Resolve-ImageIdentify {
    param([string] $MagickPath)

    $command = Get-Command "identify" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $extension = [IO.Path]::GetExtension($MagickPath)
    $candidate = Join-Path (Split-Path -Parent $MagickPath) ("identify" + $extension)
    if (Test-Path -LiteralPath $candidate) { return $candidate }

    # ImageMagick 7 on Windows commonly installs only magick.exe. It provides
    # identify as a subcommand, while ImageMagick 6 exposes identify directly.
    return $MagickPath
}

function Get-MediaManifestEntry {
    param(
        [string] $Path,
        [string] $Role,
        [string] $RepositoryRoot,
        [string] $ImageIdentifyPath
    )

    $item = Get-Item -LiteralPath $Path
    $identifyArguments = @("-format", "%w %h %n`n", $item.FullName)
    $isMagickCommand = (
        [IO.Path]::GetFileNameWithoutExtension($ImageIdentifyPath) -ieq "magick"
    )
    # Preserve a collection when ImageMagick reports one image. The `if`
    # expression otherwise unwraps a single string under StrictMode and makes
    # the required Count check unavailable on Linux ImageMagick 6.
    $metadata = @(
        if ($isMagickCommand) {
            & $ImageIdentifyPath identify @identifyArguments
        } else {
            & $ImageIdentifyPath @identifyArguments
        }
    )
    if ($LASTEXITCODE -ne 0 -or $metadata.Count -eq 0) {
        throw "Could not inspect media dimensions: $Path"
    }
    $parts = $metadata[0].Trim().Split(" ", [StringSplitOptions]::RemoveEmptyEntries)
    if ($parts.Count -ne 3) {
        throw "Unexpected media metadata for ${Path}: $($metadata[0])"
    }
    $relativePath = $item.FullName.Substring($RepositoryRoot.Length + 1).Replace("\\", "/")
    return [ordered]@{
        role = $Role
        path = $relativePath
        sha256 = (Get-FileHash -Path $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = $item.Length
        width = [int] $parts[0]
        height = [int] $parts[1]
        frameCount = [int] $parts[2]
    }
}

function Assert-YieldForecastMediaBounds {
    param(
        [object[]] $Entries,
        [int] $MaximumStillWidth,
        [int] $MaximumStillHeight,
        [int] $MaximumGifWidth
    )

    foreach ($entry in $Entries) {
        if ($entry.width -lt 1 -or $entry.height -lt 1) {
            throw "Yield media has invalid dimensions: $($entry.path)"
        }
        if ($entry.role -like "*-webp") {
            if ($entry.width -gt $MaximumStillWidth -or $entry.height -gt $MaximumStillHeight) {
                throw "Yield WebP dimensions exceed bounds: $($entry.path)"
            }
            if ($entry.bytes -gt 3MB) {
                throw "Yield WebP exceeds 3 MB: $($entry.path)"
            }
        }
        if ($entry.role -eq "evidence-gif" -and $entry.width -gt $MaximumGifWidth) {
            throw "Yield GIF width exceeds $MaximumGifWidth pixels: $($entry.path)"
        }
    }
}

function Get-PortfolioManifestEntry {
    param(
        [System.Collections.IDictionary] $Specification,
        [string] $ScreensOutputPath,
        [string] $RepositoryRoot,
        [string] $ImageIdentifyPath
    )

    $media = Get-MediaManifestEntry `
        (Join-Path $ScreensOutputPath $Specification.output) `
        "hosted-product-webp" $RepositoryRoot $ImageIdentifyPath
    return [ordered]@{
        area = $Specification.area
        route = $Specification.route
        persona = $Specification.persona
        viewport = $Specification.viewport
        role = $media.role
        path = $media.path
        sha256 = $media.sha256
        bytes = $media.bytes
        width = $media.width
        height = $media.height
        frameCount = $media.frameCount
        evidenceBoundary = "Real application UI captured from the hosted integration stack; not live production telemetry."
    }
}

function Assert-PortfolioMediaBounds {
    param([object[]] $Entries)

    if ($Entries.Count -ne 14) {
        throw "Portfolio media catalog requires 14 WebPs; found $($Entries.Count)"
    }
    foreach ($entry in $Entries) {
        if ($entry.width -lt 1 -or $entry.height -lt 1 -or $entry.frameCount -ne 1) {
            throw "Portfolio media has invalid image metadata: $($entry.path)"
        }
        if ($entry.width -gt $StillWidth -or $entry.height -gt $StillMaxHeight) {
            throw "Portfolio media dimensions exceed bounds: $($entry.path)"
        }
        if ($entry.bytes -gt 3MB) {
            throw "Portfolio WebP exceeds 3 MB: $($entry.path)"
        }
    }
}

$magick = Resolve-Magick
$imageIdentify = Resolve-ImageIdentify -MagickPath $magick
Write-Output "MEDIA_BUILD magick=$magick"

if (-not (Test-Path -LiteralPath $screensIn)) {
    throw "No captured screenshots at $screensIn. Run the guarded runner with -CaptureMedia first."
}
$captured = @(Get-ChildItem -LiteralPath $screensIn -Filter "*.png" | Sort-Object Name)
if ($captured.Count -eq 0) {
    throw "No captured screenshots found in $screensIn"
}
$missingPortfolioScreens = @(
    $portfolioScreens |
        Where-Object { -not (Test-Path -LiteralPath (Join-Path $screensIn $_.source)) } |
        ForEach-Object { $_.source }
)
if ($missingPortfolioScreens.Count -gt 0) {
    throw "Missing required portfolio captures: $($missingPortfolioScreens -join ', ')"
}

foreach ($spec in $portfolioScreens) {
    $candidate = Join-Path $screensOut $spec.output
    if (Test-Path -LiteralPath $candidate) {
        Remove-Item -LiteralPath $candidate -Force
    }
}
if (Test-Path -LiteralPath $portfolioManifestOut) {
    Remove-Item -LiteralPath $portfolioManifestOut -Force
}

New-Item -ItemType Directory -Force -Path $screensOut | Out-Null
foreach ($shot in $captured) {
    $target = Join-Path $screensOut ($shot.BaseName + ".webp")
    & $magick $shot.FullName `
        -resize "${StillWidth}x${StillMaxHeight}>" `
        -strip -quality 82 $target
    if ($LASTEXITCODE -ne 0) { throw "Failed to convert $($shot.Name)" }
    $kb = [math]::Round((Get-Item -LiteralPath $target).Length / 1KB, 1)
    Write-Output ("MEDIA_STILL {0} {1} KB" -f $shot.BaseName, $kb)
}

$frames = @(Get-ChildItem -LiteralPath $framesIn -Filter "tour-*.png" -ErrorAction SilentlyContinue |
    Sort-Object Name)
$forecastGifOut = Join-Path $repositoryRoot $ForecastGifPath
$forecastFrames = @(
    Get-ChildItem -LiteralPath $framesIn -Filter "forecast-*.png" `
        -ErrorAction SilentlyContinue |
        Sort-Object Name
)
$yieldForecastGifOut = Join-Path $repositoryRoot $YieldForecastGifPath
$yieldForecastFrames = @(
    Get-ChildItem -LiteralPath $framesIn -Filter "yield-forecast-*.png" `
        -ErrorAction SilentlyContinue |
        Sort-Object Name
)
Build-Gif -Frames $frames -OutputPath $gifOut -Label "Tour GIF"
Build-Gif -Frames $forecastFrames -OutputPath $forecastGifOut `
    -Label "Inventory forecast GIF"
Build-Gif -Frames $yieldForecastFrames -OutputPath $yieldForecastGifOut `
    -Label "Yield forecast GIF"

$yieldManifestOut = Join-Path $repositoryRoot $YieldForecastManifestPath
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $yieldManifestOut) | Out-Null
if ($env:GITHUB_ACTIONS -eq "true" -and (
    [string]::IsNullOrWhiteSpace($env:GITHUB_REPOSITORY) -or
    [string]::IsNullOrWhiteSpace($env:GITHUB_RUN_ID) -or
    [string]::IsNullOrWhiteSpace($env:GITHUB_SERVER_URL) -or
    [string]::IsNullOrWhiteSpace($env:GITHUB_SHA)
)) {
    throw "Hosted media provenance requires GITHUB_SERVER_URL, GITHUB_REPOSITORY, GITHUB_RUN_ID, and GITHUB_SHA."
}
$hostedRunUrl = if ($env:GITHUB_ACTIONS -eq "true") {
    "{0}/{1}/actions/runs/{2}" -f $env:GITHUB_SERVER_URL, $env:GITHUB_REPOSITORY, $env:GITHUB_RUN_ID
} else {
    $null
}
$portfolioManifestEntries = @(
    $portfolioScreens | ForEach-Object {
        Get-PortfolioManifestEntry $_ $screensOut $repositoryRoot $imageIdentify
    }
)
Assert-PortfolioMediaBounds -Entries $portfolioManifestEntries
$portfolioManifest = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    kind = "hosted-product-screenshots"
    provenance = [ordered]@{
        source = if ($env:GITHUB_ACTIONS -eq "true") { "github-actions" } else { "local" }
        repository = $env:GITHUB_REPOSITORY
        commitSha = $env:GITHUB_SHA
        runId = $env:GITHUB_RUN_ID
        runUrl = $hostedRunUrl
    }
    capture = [ordered]@{
        desktopViewport = "1440x900"
        mobileViewport = "390x844"
        deviceScaleFactor = 2
    }
    files = $portfolioManifestEntries
}
$portfolioManifest | ConvertTo-Json -Depth 5 | Set-Content `
    -LiteralPath $portfolioManifestOut -Encoding utf8
Write-Output "MEDIA_MANIFEST path=$PortfolioManifestPath provenance=$($portfolioManifest.provenance.source)"

$yieldManifestEntries = [System.Collections.Generic.List[object]]::new()
foreach ($screenName in @("yield-forecast-desktop.png", "yield-forecast-mobile.png")) {
    $yieldManifestEntries.Add((
        Get-MediaManifestEntry (Join-Path $screensIn $screenName) "captured-screen" $repositoryRoot $imageIdentify
    ))
}
foreach ($frame in $yieldForecastFrames) {
    $yieldManifestEntries.Add((
        Get-MediaManifestEntry $frame.FullName "captured-frame" $repositoryRoot $imageIdentify
    ))
}
foreach ($entry in @(
    (Get-MediaManifestEntry (Join-Path $screensOut "yield-forecast-desktop.webp") "desktop-webp" $repositoryRoot $imageIdentify),
    (Get-MediaManifestEntry (Join-Path $screensOut "yield-forecast-mobile.webp") "mobile-webp" $repositoryRoot $imageIdentify),
    (Get-MediaManifestEntry $yieldForecastGifOut "evidence-gif" $repositoryRoot $imageIdentify)
)) {
    $yieldManifestEntries.Add($entry)
}
Assert-YieldForecastMediaBounds -Entries @($yieldManifestEntries) `
    -MaximumStillWidth $StillWidth -MaximumStillHeight $StillMaxHeight `
    -MaximumGifWidth $GifWidth
$yieldManifest = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    provenance = [ordered]@{
        source = if ($env:GITHUB_ACTIONS -eq "true") { "github-actions" } else { "local" }
        repository = $env:GITHUB_REPOSITORY
        commitSha = $env:GITHUB_SHA
        runId = $env:GITHUB_RUN_ID
        runUrl = $hostedRunUrl
    }
    capture = [ordered]@{
        desktopViewport = "1440x900"
        mobileViewport = "390x844"
        selector = "section[aria-labelledby=yield-forecast-title]"
    }
    files = @($yieldManifestEntries)
}
$yieldManifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $yieldManifestOut -Encoding utf8
Write-Output "MEDIA_MANIFEST path=$YieldForecastManifestPath provenance=$($yieldManifest.provenance.source)"
Write-Output "MEDIA_BUILD=PASS"
