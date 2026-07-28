[CmdletBinding()]
param(
    [switch] $HostedCi
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRoot = Join-Path $repositoryRoot "backend"
$isWindowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$powerShellCommand = if ($isWindowsHost) { "powershell" } else { "pwsh" }
$workspaceDiskGuard = Join-Path $repositoryRoot "scripts\check-workspace-disk.ps1"
$hostedDiskGuard = Join-Path $repositoryRoot "scripts\check-hosted-ci-disk.ps1"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)] [string] $Command,
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $Failure
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw $Failure
    }
}

function Resolve-HostedRunnerTemp {
    if ($isWindowsHost -or
            $env:GITHUB_ACTIONS -ne "true" -or
            $env:RUNNER_ENVIRONMENT -ne "github-hosted" -or
            $env:RUNNER_OS -ne "Linux" -or
            $env:CI -ne "true" -or
            [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        throw "HostedCi is restricted to GitHub-hosted Linux runners."
    }
    $runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP)
    if (-not [IO.Path]::IsPathRooted($runnerTemp) -or
            -not (Test-Path -LiteralPath $runnerTemp -PathType Container)) {
        throw "RUNNER_TEMP must resolve to an existing rooted hosted-runner directory."
    }
    return $runnerTemp
}

function Invoke-DiskGuard {
    if ($HostedCi) {
        $runnerTemp = Resolve-HostedRunnerTemp
        Invoke-Checked $powerShellCommand @(
            "-NoProfile", "-File", $hostedDiskGuard, "-Path", $runnerTemp
        ) "Hosted CI disk guard failed."
        return
    }

    $output = @(& $powerShellCommand -NoProfile -ExecutionPolicy Bypass -File $workspaceDiskGuard 2>&1)
    $exitCode = $LASTEXITCODE
    $output | Write-Output
    if ($exitCode -ne 0 -or ($output -join "`n") -notmatch "DISK_GUARD overall=PASS") {
        throw "Workspace disk guard is not PASS; realtime E2E was not started."
    }
}

function Assert-ReviewedMavenConfiguration {
    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_ARGS) -or
            -not [string]::IsNullOrWhiteSpace($env:MAVEN_CONFIG) -or
            -not [string]::IsNullOrWhiteSpace($env:MAVEN_PROJECTBASEDIR)) {
        throw "MAVEN_ARGS, MAVEN_CONFIG, and MAVEN_PROJECTBASEDIR must be unset; hidden Maven arguments are not allowed by the realtime E2E runner."
    }
    $projectMavenConfig = Join-Path $backendRoot ".mvn\maven.config"
    if (Test-Path -LiteralPath $projectMavenConfig) {
        throw "A project .mvn/maven.config is not permitted by the realtime E2E runner; pass only reviewed arguments explicitly."
    }

    $hiddenMavenOptions = "$($env:MAVEN_OPTS) $($env:JAVA_TOOL_OPTIONS) $($env:_JAVA_OPTIONS)"
    $blockedPropertyNames = "(?:test|it\.test|failIfNoTests|skipTests|maven\.test\.skip|skipITs|skipIT|surefire\.skip|failsafe\.skip|surefire\.excludes|failsafe\.excludes|surefire\.includes|failsafe\.includes|argLine|maven\.repo\.local|java\.io\.tmpdir|user\.home|project\.build\.directory|project\.reporting\.outputDirectory)"
    $blockedOptionPattern = "(?:--fail-never|(?:^|\s)-fn(?:\s|$)|(?:-D|--define)\s*=?\s*$blockedPropertyNames(?:=|\s|$))"
    if ($hiddenMavenOptions -match $blockedOptionPattern) {
        throw "MAVEN_OPTS and Java tool options contain a blocked test-skip or output-redirection option."
    }
}

function Get-TestcontainersResourceIds {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("Container", "Network", "Volume")]
        [string] $ResourceType
    )

    $arguments = switch ($ResourceType) {
        "Container" { @("ps", "-aq", "--filter", "label=org.testcontainers") }
        "Network" { @("network", "ls", "-q", "--filter", "label=org.testcontainers") }
        "Volume" { @("volume", "ls", "-q", "--filter", "label=org.testcontainers") }
    }
    $ids = @(& docker @arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect Testcontainers $ResourceType resources."
    }
    return @($ids | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Assert-NoNewTestcontainersResources {
    param([hashtable] $Before)

    $resourceTypes = @("Container", "Network", "Volume")
    for ($attempt = 1; $attempt -le 30; $attempt += 1) {
        $remaining = [System.Collections.Generic.List[string]]::new()
        foreach ($resourceType in $resourceTypes) {
            $beforeSet = [System.Collections.Generic.HashSet[string]]::new(
                [System.StringComparer]::Ordinal)
            foreach ($resourceId in $Before[$resourceType]) {
                [void] $beforeSet.Add($resourceId)
            }
            foreach ($resourceId in (Get-TestcontainersResourceIds -ResourceType $resourceType)) {
                if (-not $beforeSet.Contains($resourceId)) {
                    $remaining.Add("${resourceType}:$resourceId")
                }
            }
        }
        if ($remaining.Count -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "Owned Testcontainers resources remain after the E2E run; no unrelated Docker resource was removed."
}

function Assert-FailsafeReports {
    $reportRoot = Join-Path $backendRoot "target\failsafe-reports"
    $testNames = @(
        "RealtimeOutboxKafkaE2eIntegrationTest",
        "KafkaRealtimeDeadLetterIntegrationTest",
        "KafkaRealtimePoisonRecordIntegrationTest"
    )
    foreach ($testName in $testNames) {
        $report = Get-ChildItem -LiteralPath $reportRoot -Filter "TEST-*$testName.xml" |
            Select-Object -First 1
        if ($null -eq $report) {
            throw "Missing Failsafe report for $testName."
        }
        [xml] $result = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $result.testsuite
        if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
            throw "Failsafe report for $testName contains failures, errors, or skips."
        }
    }
}

function Reset-FailsafeReports {
    $reportRoot = Join-Path $backendRoot "target\failsafe-reports"
    if (Test-Path -LiteralPath $reportRoot) {
        Remove-Item -LiteralPath $reportRoot -Recurse -Force
    }
}

Invoke-DiskGuard
Assert-ReviewedMavenConfiguration

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI is required for the realtime E2E gate."
}
Invoke-Checked "docker" @("info", "--format", "{{.ServerVersion}}") "Docker daemon is unavailable."

$runtimeRoot = if ($HostedCi) {
    Join-Path (Resolve-HostedRunnerTemp) "agriinsight-realtime-e2e"
} else {
    Join-Path $repositoryRoot "artifacts\_tmp\realtime-e2e"
}
if ($isWindowsHost -and -not $HostedCi -and [IO.Path]::GetPathRoot($runtimeRoot) -ne "D:\") {
    throw "Local realtime E2E runtime must resolve to D:, received $runtimeRoot."
}

$mavenRepository = Join-Path $runtimeRoot "m2-repository"
$javaTemp = Join-Path $runtimeRoot "java-tmp"
$mavenUserHome = Join-Path $runtimeRoot "maven-user-home"
New-Item -ItemType Directory -Force -Path $mavenRepository, $javaTemp, $mavenUserHome | Out-Null

$mavenWrapperName = if ($isWindowsHost) { "mvnw.cmd" } else { "mvnw" }
$mavenWrapper = Join-Path $backendRoot $mavenWrapperName
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven wrapper is missing at $mavenWrapper."
}

Reset-FailsafeReports
$beforeResources = @{
    Container = @(Get-TestcontainersResourceIds -ResourceType Container)
    Network = @(Get-TestcontainersResourceIds -ResourceType Network)
    Volume = @(Get-TestcontainersResourceIds -ResourceType Volume)
}
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
$previousMavenHome = $env:MAVEN_USER_HOME
$runError = $null

try {
    $env:TEMP = $javaTemp
    $env:TMP = $javaTemp
    $env:MAVEN_USER_HOME = $mavenUserHome
    Push-Location $backendRoot
    try {
        Invoke-Checked $mavenWrapper @(
            "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=$mavenRepository", "-Djava.io.tmpdir=$javaTemp",
            "-Dit.test=RealtimeOutboxKafkaE2eIntegrationTest,KafkaRealtimeDeadLetterIntegrationTest,KafkaRealtimePoisonRecordIntegrationTest",
            "test-compile", "failsafe:integration-test", "failsafe:verify"
        ) "Realtime Kafka E2E Failsafe gate failed."
    } finally {
        Pop-Location
    }
    Assert-FailsafeReports
} catch {
    $runError = $_
} finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
    $env:MAVEN_USER_HOME = $previousMavenHome
}

$cleanupError = $null
try {
    Assert-NoNewTestcontainersResources $beforeResources
} catch {
    $cleanupError = $_
}
if ($null -ne $runError) {
    if ($null -ne $cleanupError) {
        throw "$($runError.Exception.Message) Cleanup also failed: $($cleanupError.Exception.Message)"
    }
    throw $runError
}
if ($null -ne $cleanupError) {
    throw $cleanupError
}

Write-Output "REALTIME_E2E=PASS kafka=apache-kafka-4.3.1 postgres=testcontainers cleanup=owned"
