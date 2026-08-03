[CmdletBinding()]
param(
    [switch]$HostedCi,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendRoot = Join-Path $projectRoot "backend"
Import-Module (Join-Path $projectRoot "scripts\recovery-runtime-helpers.psm1") -Force

function Resolve-GuardedOutputPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    } else {
        Join-Path $projectRoot $Path
    }
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if ($HostedCi) {
        $runnerTemp = Assert-GitHubHostedRecoveryRunner
        $runnerPrefix = $runnerTemp.TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar
        ) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($runnerPrefix, [System.StringComparison]::Ordinal)) {
            throw "$Name must resolve inside RUNNER_TEMP for hosted recovery; received $resolved."
        }
    }
    elseif ([System.IO.Path]::GetPathRoot($resolved) -ne "D:\") {
        throw "$Name must resolve to the D drive; received $resolved."
    }
    return $resolved
}

Invoke-RecoveryDiskGuard -ProjectRoot $projectRoot -HostedCi:$HostedCi

if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_ARGS) -or
        -not [string]::IsNullOrWhiteSpace($env:MAVEN_CONFIG) -or
        -not [string]::IsNullOrWhiteSpace($env:MAVEN_PROJECTBASEDIR)) {
    throw "MAVEN_ARGS, MAVEN_CONFIG, and MAVEN_PROJECTBASEDIR must be unset; hidden Maven arguments are not allowed by the guarded runner."
}
$projectMavenConfig = Join-Path $backendRoot ".mvn\maven.config"
if (Test-Path -LiteralPath $projectMavenConfig) {
    throw "A project .mvn/maven.config is not permitted by the guarded runner; pass only reviewed arguments explicitly."
}

$hiddenMavenOptions = "$($env:MAVEN_OPTS) $($env:JAVA_TOOL_OPTIONS) $($env:_JAVA_OPTIONS)"
$blockedPropertyNames = "(?:test|it\.test|failIfNoTests|skipTests|maven\.test\.skip|skipITs|skipIT|surefire\.skip|failsafe\.skip|surefire\.excludes|failsafe\.excludes|surefire\.includes|failsafe\.includes|argLine|maven\.repo\.local|java\.io\.tmpdir|user\.home|project\.build\.directory|project\.reporting\.outputDirectory)"
$blockedOptionPattern = "(?:--fail-never|(?:^|\s)-fn(?:\s|$)|(?:-D|--define)\s*=?\s*$blockedPropertyNames(?:=|\s|$))"
if ($hiddenMavenOptions -match $blockedOptionPattern) {
    throw "MAVEN_OPTS and JAVA_TOOL_OPTIONS contain a blocked test-skip or output-redirection option."
}

$defaultRuntimeRoot = if ($HostedCi) {
    Join-Path (Assert-GitHubHostedRecoveryRunner) "agriinsight-backend-runner"
} else {
    Join-Path $projectRoot "artifacts\_tmp"
}
$repoLocal = if ($env:MAVEN_REPO_LOCAL) {
    $env:MAVEN_REPO_LOCAL
} else {
    Join-Path $defaultRuntimeRoot "m2-repository"
}
$javaTemp = if ($env:MAVEN_TEMP_DIR) {
    $env:MAVEN_TEMP_DIR
} else {
    Join-Path $defaultRuntimeRoot "java-tmp"
}
$mavenUserHome = if ($env:MAVEN_USER_HOME) {
    $env:MAVEN_USER_HOME
} else {
    Join-Path $defaultRuntimeRoot "maven-user-home"
}

$arguments = @($MavenArguments)
$guardedMavenArguments = @($MavenArguments)
$flywayGoalRequested = @($MavenArguments | Where-Object {
    $_ -in @("flyway:migrate", "flyway:validate")
}).Count -gt 0
if ($flywayGoalRequested) {
    # Flyway 12.4 rejects the Maven plugin's legacy pluralized extension key.
    # Use the documented PostgreSQL namespace property for concurrent indexes.
    $guardedMavenArguments = @("-Dflyway.postgresql.transactional.lock=false") + $guardedMavenArguments
}
$blockedTestPropertyPattern = "^(?:test|it\.test|failIfNoTests|surefire\.skip|failsafe\.skip|surefire\.excludes|failsafe\.excludes|surefire\.includes|failsafe\.includes|argLine)(?:=|$)"
for ($index = 0; $index -lt $arguments.Count; $index++) {
    $argument = $arguments[$index]
    $definedProperty = $null

    if ($argument -in @(
            "-f", "--file", "-s", "--settings", "-gs", "--global-settings",
            "-pl", "--projects", "-N", "--non-recursive", "-rf", "--resume-from") -or
            $argument -match "^(?:-f.+|-s.+|-gs.+|-pl.+|-rf.+|--file=.+|--settings=.+|--global-settings=.+|--projects=.+|--resume-from=.+)$") {
        throw "Alternate Maven project, settings, or module selectors are not permitted by the guarded runner."
    }

    if ($argument -eq "-D" -or $argument -eq "--define") {
        if ($index + 1 -ge $arguments.Count) {
            throw "A Maven property flag is missing its value."
        }
        $index++
        $definedProperty = $arguments[$index]
    } elseif ($argument.StartsWith("--define=", [System.StringComparison]::OrdinalIgnoreCase)) {
        $definedProperty = $argument.Substring("--define=".Length)
    } elseif ($argument.StartsWith("-D", [System.StringComparison]::OrdinalIgnoreCase)) {
        $definedProperty = $argument.Substring(2)
    }

    if ($null -ne $definedProperty -and
            $definedProperty -match "^(maven\.repo\.local|java\.io\.tmpdir|user\.home|project\.build\.directory|project\.reporting\.outputDirectory)(?:=|$)") {
        throw "Use MAVEN_REPO_LOCAL or MAVEN_TEMP_DIR instead of overriding output paths in Maven arguments."
    }

    if ($argument -in @("--fail-never", "-fn") -or
            (($argument -match "^-D(?:skipTests|maven\.test\.skip|skipITs|skipIT)(?:=|$)") -and
             ($argument -notmatch "=false$")) -or
            ($null -ne $definedProperty -and
             $definedProperty -match $blockedTestPropertyPattern) -or
            ($null -ne $definedProperty -and
             ($definedProperty -match "^(?:skipTests|maven\.test\.skip|skipITs|skipIT)(?:=|$)") -and
             ($definedProperty -notmatch "=false$"))) {
        throw "The backend verification wrapper does not permit flags that skip or mask test failures."
    }
}

$verifyRequested = $MavenArguments | Where-Object { $_ -eq "verify" }
if ($verifyRequested) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI is required for the verify gate but was not found."
    }
    $dockerInfo = & docker info --format '{{.ServerVersion}}' 2>&1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($dockerInfo -join "`n"))) {
        throw "Docker daemon is required for the verify gate and is not available."
    }
}

$repoLocal = Resolve-GuardedOutputPath -Path $repoLocal -Name "MAVEN_REPO_LOCAL"
$javaTemp = Resolve-GuardedOutputPath -Path $javaTemp -Name "MAVEN_TEMP_DIR"
$mavenUserHome = Resolve-GuardedOutputPath -Path $mavenUserHome -Name "MAVEN_USER_HOME"

New-Item -ItemType Directory -Force -Path $repoLocal, $javaTemp, $mavenUserHome | Out-Null
$env:MAVEN_USER_HOME = $mavenUserHome
$env:TEMP = $javaTemp
$env:TMP = $javaTemp
$env:MAVEN_OPTS = "$($env:MAVEN_OPTS) -Djava.io.tmpdir=$javaTemp".Trim()
$env:JAVA_TOOL_OPTIONS = "$($env:JAVA_TOOL_OPTIONS) -Djava.io.tmpdir=$javaTemp".Trim()
$isWindowsHost = [System.IO.Path]::DirectorySeparatorChar -eq '\'
$mavenWrapperName = if ($isWindowsHost) { "mvnw.cmd" } else { "mvnw" }
$mavenWrapper = Join-Path $backendRoot $mavenWrapperName
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper not found at $mavenWrapper. Generate it before running backend tests."
}

Push-Location $backendRoot
try {
    & $mavenWrapper "-Dmaven.repo.local=$repoLocal" @guardedMavenArguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
