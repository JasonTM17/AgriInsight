[CmdletBinding()]
param(
    [switch] $SkipStaticGates,
    [switch] $RunLifecycleProbe,
    [switch] $CaptureMedia,
    [switch] $HostedCi
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$projectName = "agriinsight-web-e2e"
$runnerMutexName = "Global\AgriInsight.WebE2E.Runner"
$isWindowsHost = (
    [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
)
if (-not $isWindowsHost) {
    $runnerMutexName = "AgriInsight.WebE2E.Runner"
}
$powerShellCommand = if ($isWindowsHost) { "powershell" } else { "pwsh" }
$artifactRuntimeRoot = Join-Path $repositoryRoot "artifacts\_tmp\web-e2e"
$temporaryRuntimeRoot = Join-Path $repositoryRoot "_tmp\web-e2e"
$mavenRepositoryRoot = Join-Path $repositoryRoot "_tmp\host-caches\maven-repository"
$artifactRuntimeParent = Join-Path $repositoryRoot "artifacts/_tmp"
if (-not $isWindowsHost) {
    $artifactRuntimeRoot = Join-Path $repositoryRoot "artifacts/_tmp/web-e2e"
    $temporaryRuntimeRoot = Join-Path $repositoryRoot "_tmp/web-e2e"
    $mavenRepositoryRoot = Join-Path $repositoryRoot "_tmp/host-caches/maven-repository"
}
if ($HostedCi) {
    if (
        $env:CI -ne "true" -or
        [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)
    ) {
        throw "HostedCi requires CI=true and RUNNER_TEMP."
    }
    $artifactRuntimeParent = Join-Path $env:RUNNER_TEMP "agriinsight-web-e2e"
    $artifactRuntimeRoot = Join-Path $artifactRuntimeParent "artifacts"
    $mavenRepositoryRoot = Join-Path $artifactRuntimeParent "maven-repository"
}
$postgresRuntimeRoot = Join-Path $artifactRuntimeRoot "postgres"
$composeEnvironmentReady = $false
$runnerMutex = $null
$runnerMutexHeld = $false
$analyticsProcess = $null
$backendProcess = $null
$analyticsLogPath = Join-Path $artifactRuntimeRoot "analytics.log"
$backendLogPath = Join-Path $artifactRuntimeRoot "backend.log"
$composeFiles = @(
    "compose.yaml",
    "compose.backend.yaml",
    "compose.demo.yaml",
    "compose.web-e2e.yaml"
)

function New-HexSecret {
    param([int] $ByteCount = 32)
    $bytes = [byte[]]::new($ByteCount)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return ([BitConverter]::ToString($bytes) -replace "-", "").ToLowerInvariant()
}

function New-Base64Key {
    $bytes = [byte[]]::new(32)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)] [string] $Command,
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $Failure
    )
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw $Failure }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $Failure
    )
    $composeArguments = @("compose", "-p", $projectName)
    foreach ($file in $composeFiles) {
        $composeArguments += @("-f", $file)
    }
    $composeArguments += $Arguments
    Invoke-Checked "docker" $composeArguments $Failure
}

function Get-TcpPortListeners {
    param([Parameter(Mandatory = $true)] [int] $Port)
    if (-not $isWindowsHost) {
        return @()
    }
    return @(
        Get-NetTCPConnection `
            -LocalPort $Port `
            -State Listen `
            -ErrorAction SilentlyContinue
    )
}

function Assert-TcpPortAvailable {
    param([Parameter(Mandatory = $true)] [int] $Port)
    if (-not $isWindowsHost) {
        $listener = [Net.Sockets.TcpListener]::new(
            [Net.IPAddress]::Loopback,
            $Port
        )
        try {
            $listener.Start()
        } catch {
            throw "TCP port $Port is already in use"
        } finally {
            $listener.Stop()
        }
        return
    }
    $listeners = @(Get-TcpPortListeners $Port)
    if ($listeners.Count -gt 0) {
        $owners = ($listeners.OwningProcess | Sort-Object -Unique) -join ","
        throw "TCP port $Port is already owned by process $owners"
    }
}

function Resolve-JavaExecutable {
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        throw "Web E2E requires JAVA_HOME to launch an owned Java process"
    }
    $javaExecutable = if ($isWindowsHost) {
        Join-Path $env:JAVA_HOME "bin\java.exe"
    } else {
        Join-Path $env:JAVA_HOME "bin/java"
    }
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw "JAVA_HOME does not contain the Java executable"
    }
    return $javaExecutable
}

function Assert-ProcessOwnsTcpPort {
    param(
        [Parameter(Mandatory = $true)] [System.Diagnostics.Process] $LauncherProcess,
        [Parameter(Mandatory = $true)] [int] $Port
    )
    $listeners = @(Get-TcpPortListeners $Port)
    if (-not ($listeners.OwningProcess -contains $LauncherProcess.Id)) {
        throw "Process $($LauncherProcess.Id) does not own TCP port $Port"
    }
}

function Wait-OwnedHttpProcessReady {
    param(
        [Parameter(Mandatory = $true)] [string] $Uri,
        [Parameter(Mandatory = $true)] [System.Diagnostics.Process] $LauncherProcess,
        [Parameter(Mandatory = $true)] [int] $Port,
        [int] $Attempts = 60
    )
    $lastFailure = "no readiness response"
    for ($attempt = 1; $attempt -le $Attempts; $attempt += 1) {
        if ($LauncherProcess.HasExited) {
            throw "Process $($LauncherProcess.Id) exited before $Uri became ready"
        }
        try {
            $response = Invoke-WebRequest -Uri $Uri -TimeoutSec 3 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                if ($isWindowsHost) {
                    Assert-ProcessOwnsTcpPort $LauncherProcess $Port
                }
                return
            }
            $lastFailure = "HTTP $($response.StatusCode)"
        } catch {
            $lastFailure = $_.Exception.Message
        }
        if ($LauncherProcess.HasExited) {
            throw "Process $($LauncherProcess.Id) exited before $Uri became ready"
        }
        if ($attempt -eq $Attempts) {
            throw "Timed out waiting for $Uri; last failure: $lastFailure"
        }
        Start-Sleep -Seconds 2
    }
}

function Start-GuardedProcess {
    param(
        [Parameter(Mandatory = $true)] [string] $FilePath,
        [Parameter(Mandatory = $true)] [string[]] $ArgumentList,
        [Parameter(Mandatory = $true)] [string] $WorkingDirectory,
        [Parameter(Mandatory = $true)] [string] $StandardOutput,
        [Parameter(Mandatory = $true)] [string] $StandardError
    )
    $parameters = @{
        FilePath = $FilePath
        ArgumentList = $ArgumentList
        WorkingDirectory = $WorkingDirectory
        RedirectStandardOutput = $StandardOutput
        RedirectStandardError = $StandardError
        PassThru = $true
    }
    if ($isWindowsHost) {
        $parameters.WindowStyle = "Hidden"
    }
    return Start-Process @parameters
}

function Invoke-WorkspaceDiskGuard {
    if ($HostedCi) {
        if (
            $env:CI -ne "true" -or
            [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)
        ) {
            throw "HostedCi requires CI=true and RUNNER_TEMP."
        }
        Invoke-Checked $powerShellCommand @(
            "-NoProfile",
            "-File", "scripts/check-hosted-ci-disk.ps1",
            "-Path", $env:RUNNER_TEMP
        ) "Hosted CI runner.temp disk guard failed"
        return
    }
    Invoke-Checked $powerShellCommand @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/check-workspace-disk.ps1"
    ) "Workspace disk guard failed"
}

function Remove-SafeRuntimeDirectory {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $ExpectedParent
    )
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $resolvedParent = [IO.Path]::GetFullPath($ExpectedParent)
    if (-not $resolvedPath.StartsWith(
        $resolvedParent + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Refusing to remove runtime directory outside $resolvedParent"
    }
    if ($HostedCi -and -not $isWindowsHost) {
        $runnerOwner = "$(& id -u):$(& id -g)"
        if ($LASTEXITCODE -ne 0 -or $runnerOwner -notmatch "^\d+:\d+$") {
            throw "Could not resolve hosted runner ownership"
        }
        Invoke-Checked "sudo" @(
            "chown", "--recursive", $runnerOwner, "--", $resolvedPath
        ) "Could not restore hosted runtime ownership"
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

function Assert-E2eProjectStopped {
    $containerIds = @(
        & docker ps -aq `
            --filter "label=com.docker.compose.project=$projectName"
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect web E2E project containers"
    }
    if ($containerIds.Count -gt 0) {
        throw "Refusing runtime cleanup while web E2E containers still exist"
    }
}

function Stop-OwnedProcess {
    param(
        [System.Diagnostics.Process] $LauncherProcess,
        [Parameter(Mandatory = $true)] [string] $Name
    )
    if ($null -eq $LauncherProcess) { return }
    try {
        if (-not $LauncherProcess.HasExited) {
            $LauncherProcess.Kill()
        }
    } catch [InvalidOperationException] {
        if (-not $LauncherProcess.HasExited) { throw }
    }
    try {
        if (
            -not $LauncherProcess.HasExited -and
            -not $LauncherProcess.WaitForExit(10000)
        ) {
            throw "$Name E2E process did not stop"
        }
        $LauncherProcess.WaitForExit()
    } finally {
        $LauncherProcess.Dispose()
    }
}

function Invoke-CleanupStep {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [scriptblock] $Action,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[string]] $Errors
    )
    try {
        & $Action
    } catch {
        $message = "$Name`: $($_.Exception.Message)"
        $Errors.Add($message)
        Write-Warning $message
    }
}

if ($RunLifecycleProbe) {
    $probeErrors = [System.Collections.Generic.List[string]]::new()
    $probeState = [pscustomobject]@{ CleanupContinued = $false }
    Invoke-CleanupStep "injected cleanup failure" {
        throw "injected lifecycle probe failure"
    } $probeErrors
    Invoke-CleanupStep "cleanup continuation" {
        $probeState.CleanupContinued = $true
    } $probeErrors
    if (-not $probeState.CleanupContinued -or $probeErrors.Count -ne 1) {
        throw "Lifecycle cleanup continuation probe failed"
    }

    $probeRoot = Join-Path (
        Join-Path $repositoryRoot "_tmp"
    ) ("web-e2e-lifecycle-probe-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $probeRoot | Out-Null
    $probeProcess = $null
    try {
        $probeProcess = Start-Process `
            -FilePath "powershell.exe" `
            -ArgumentList @("-NoProfile", "-Command", "Start-Sleep -Seconds 30") `
            -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $probeRoot "probe.log") `
            -RedirectStandardError (Join-Path $probeRoot "probe-error.log") `
            -PassThru
        $probeProcessId = $probeProcess.Id
        Stop-OwnedProcess $probeProcess "Probe"
        $probeProcess = $null
        if (Get-Process -Id $probeProcessId -ErrorAction SilentlyContinue) {
            throw "Lifecycle owned-process probe left a process running"
        }
    } finally {
        if ($null -ne $probeProcess) {
            Stop-OwnedProcess $probeProcess "Probe"
        }
        Remove-SafeRuntimeDirectory $probeRoot (
            Join-Path $repositoryRoot "_tmp"
        )
    }
    Write-Output (
        "LIFECYCLE_PROBE=PASS cleanup_continued=true " +
        "owned_process_stopped=true"
    )
    exit 0
}

Push-Location $repositoryRoot
$backendJar = Join-Path (
    Join-Path $repositoryRoot "backend"
) "target/agriinsight-backend-0.1.0-SNAPSHOT.jar"
$javaExecutable = Resolve-JavaExecutable
$previousPythonPath = $env:PYTHONPATH
$runError = $null
$cleanupErrors = [System.Collections.Generic.List[string]]::new()
try {
    $runnerMutex = [Threading.Mutex]::new($false, $runnerMutexName)
    try {
        $runnerMutexHeld = $runnerMutex.WaitOne(0)
    } catch [Threading.AbandonedMutexException] {
        $runnerMutexHeld = $true
    }
    if (-not $runnerMutexHeld) {
        throw "Another AgriInsight web E2E runner already owns the workspace"
    }

    Invoke-WorkspaceDiskGuard

    if ((& node --version) -ne "v24.12.0") {
        throw "Web E2E requires Node v24.12.0"
    }
    if ((& npm --version) -ne "11.6.2") {
        throw "Web E2E requires npm 11.6.2"
    }
    Invoke-Checked "docker" @("info") "Docker daemon is required for web E2E"
    Assert-TcpPortAvailable 58081
    Assert-TcpPortAvailable 58082

    $operatorPassword = New-HexSecret
    $backendMigratorPassword = New-HexSecret
    $backendRuntimePassword = New-HexSecret
    $webMigratorPassword = New-HexSecret
    $webRuntimePassword = New-HexSecret
    $clientSecret = New-HexSecret
    $personaPassword = New-HexSecret 16

    $env:AGRIINSIGHT_DEMO_POSTGRES_DATA_DIR = $postgresRuntimeRoot
    $env:AGRIINSIGHT_POSTGRES_PORT = "55443"
    $env:AGRIINSIGHT_BACKEND_PORT = "58081"
    $env:AGRIINSIGHT_POSTGRES_OPERATOR_PASSWORD = $operatorPassword
    $env:AGRIINSIGHT_DB_MIGRATOR_PASSWORD = $backendMigratorPassword
    $env:AGRIINSIGHT_DB_RUNTIME_PASSWORD = $backendRuntimePassword
    $env:AGRIINSIGHT_OIDC_ISSUER_URI = "http://localhost:58080/realms/agriinsight-demo"
    $env:AGRIINSIGHT_OIDC_API_AUDIENCE = "agriinsight-api"
    $env:AGRIINSIGHT_OIDC_INTERACTIVE_CLIENT_ID = "agriinsight-web"
    $env:AGRIINSIGHT_DEMO_KEYCLOAK_PORT = "58080"
    $env:AGRIINSIGHT_DEMO_KEYCLOAK_ADMIN_USERNAME = "agriinsight-e2e-admin"
    $env:AGRIINSIGHT_DEMO_KEYCLOAK_ADMIN_PASSWORD = New-HexSecret
    $env:AGRIINSIGHT_DEMO_OIDC_CLIENT_SECRET = $clientSecret
    $env:AGRIINSIGHT_DEMO_TENANT_ADMIN_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_EXECUTIVE_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_FARM_MANAGER_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_INVENTORY_MANAGER_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_ANALYST_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_FIELD_WORKER_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_DEMO_SUPPLIER_PASSWORD = $personaPassword

    $encodedOperatorPassword = [Uri]::EscapeDataString($operatorPassword)
    $encodedWebMigratorPassword = [Uri]::EscapeDataString($webMigratorPassword)
    $encodedWebRuntimePassword = [Uri]::EscapeDataString($webRuntimePassword)
    $databaseOrigin = "127.0.0.1:55443/agriinsight_demo"
    $env:AGRIINSIGHT_WEB_BASE_URL = "http://localhost:3100"
    $env:AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100"
    $env:AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS = "false"
    $env:AGRIINSIGHT_WEB_OIDC_ISSUER = $env:AGRIINSIGHT_OIDC_ISSUER_URI
    $env:AGRIINSIGHT_WEB_OIDC_CLIENT_ID = "agriinsight-web"
    $env:AGRIINSIGHT_WEB_OIDC_CLIENT_SECRET = $clientSecret
    $env:AGRIINSIGHT_WEB_SESSION_DATABASE_URL = (
        "postgresql://agriinsight_web_runtime:{0}@{1}" -f
        $encodedWebRuntimePassword,
        $databaseOrigin
    )
    $env:AGRIINSIGHT_WEB_MIGRATOR_DATABASE_URL = (
        "postgresql://agriinsight_web_migrator:{0}@{1}" -f
        $encodedWebMigratorPassword,
        $databaseOrigin
    )
    $env:AGRIINSIGHT_WEB_SESSION_ENCRYPTION_KEY_BASE64 = New-Base64Key
    $env:AGRIINSIGHT_WEB_TOKEN_KEY_ID = "e2e-v1"
    $env:AGRIINSIGHT_WEB_SESSION_PREVIOUS_KEYS_JSON = "{}"
    $env:AGRIINSIGHT_WEB_CSRF_KEY_BASE64 = New-Base64Key
    $env:AGRIINSIGHT_WEB_SESSION_LIFETIME_SECONDS = "28800"
    $env:AGRIINSIGHT_BACKEND_BASE_URL = "http://127.0.0.1:58081"
    $env:AGRIINSIGHT_ANALYTICS_BASE_URL = "http://127.0.0.1:58082"
    $env:AGRIINSIGHT_WEB_E2E_USERNAME = "executive"
    $env:AGRIINSIGHT_WEB_E2E_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_WEB_E2E_PERSONA_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_WEB_E2E_WORK_USERNAME = "field-worker"
    $env:AGRIINSIGHT_WEB_E2E_WORK_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_WEB_E2E_DENIED_USERNAME = "supplier"
    $env:AGRIINSIGHT_WEB_E2E_DENIED_PASSWORD = $personaPassword
    $env:AGRIINSIGHT_WEB_E2E_INVENTORY_USERNAME = "inventory-manager"
    $env:AGRIINSIGHT_WEB_E2E_INVENTORY_PASSWORD = $personaPassword
    $composeEnvironmentReady = $true

    try {
        Invoke-Compose @(
            "--profile", "backend", "down", "--remove-orphans"
        ) "Could not clear prior web E2E services"
    } catch {
        Write-Warning $_
    }
    Assert-E2eProjectStopped
    Remove-SafeRuntimeDirectory $artifactRuntimeRoot (
        $artifactRuntimeParent
    )
    Remove-SafeRuntimeDirectory $temporaryRuntimeRoot (
        Join-Path $repositoryRoot "_tmp"
    )
    New-Item -ItemType Directory -Force -Path (
        Join-Path $artifactRuntimeRoot "npm-cache"
    ), (
        Join-Path $artifactRuntimeRoot "temp"
    ), $mavenRepositoryRoot, $postgresRuntimeRoot | Out-Null
    $env:npm_config_cache = Join-Path $artifactRuntimeRoot "npm-cache"
    $env:TEMP = Join-Path $artifactRuntimeRoot "temp"
    $env:TMP = $env:TEMP

    if (-not $SkipStaticGates) {
        Invoke-Checked "npm" @(
            "--prefix", "web", "ci", "--ignore-scripts"
        ) "npm ci failed"
        Invoke-Checked "npm" @("--prefix", "web", "run", "contracts:check") (
            "Generated contract drift check failed"
        )
        Invoke-Checked "npm" @("--prefix", "web", "run", "typecheck") (
            "Web typecheck failed"
        )
        Invoke-Checked "npm" @("--prefix", "web", "test") "Web unit tests failed"
        Invoke-Checked "npm" @("--prefix", "web", "run", "lint") "Web lint failed"
        Invoke-Checked "npm" @("--prefix", "web", "run", "build") "Web build failed"
        Invoke-Checked "mvn" @(
            "-Dmaven.repo.local=$mavenRepositoryRoot",
            "-q", "-f", "backend/pom.xml", "-DskipTests", "package"
        ) "Backend package build failed"
    } else {
        Write-Output "STATIC_GATES=SKIPPED reason=local-e2e-iteration"
    }

    Invoke-Compose @("config", "--quiet") "Web E2E Compose validation failed"
    try {
        Invoke-Compose @(
            "--profile", "backend", "up", "-d", "--wait",
            "postgres", "backend-role-bootstrap", "backend-migrate", "keycloak"
        ) "Web E2E infrastructure did not become healthy"
    } catch {
        Write-Output "--- backend-role-bootstrap logs ---"
        try {
            Invoke-Compose @("logs", "--no-color", "backend-role-bootstrap") (
                "Could not collect backend role bootstrap logs"
            )
        } catch {
            Write-Warning $_
        }
        throw
    }

    $env:AGRIINSIGHT_DEMO_KEYCLOAK_CONTAINER = (
        & docker compose -p $projectName `
            -f compose.yaml `
            -f compose.backend.yaml `
            -f compose.demo.yaml `
            -f compose.web-e2e.yaml ps -q keycloak
    )
    if ([string]::IsNullOrWhiteSpace($env:AGRIINSIGHT_DEMO_KEYCLOAK_CONTAINER)) {
        throw "Keycloak E2E container was not found"
    }
    Invoke-Checked $powerShellCommand @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/configure-demo-oidc.ps1"
    ) "Demo OIDC configuration failed"

    $env:PGPASSWORD = $backendMigratorPassword
    $bootstrapArguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/bootstrap-demo-environment.ps1",
        "-ConfirmLocalDemo",
        "-DatabasePort", "55443",
        "-OutputDirectory", "_tmp/web-e2e/demo-bootstrap"
    )
    $revocationArguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/test-demo-assignment-revocation.ps1",
        "-DatabasePort", "55443",
        "-OutputDirectory", "_tmp/web-e2e/demo-bootstrap"
    )
    if ($HostedCi) {
        $bootstrapArguments += @(
            "-HostedCi",
            "-HostedCiTempPath", $env:RUNNER_TEMP
        )
        $revocationArguments += @(
            "-HostedCi",
            "-HostedCiTempPath", $env:RUNNER_TEMP
        )
    }
    Invoke-Checked $powerShellCommand $bootstrapArguments (
        "Guarded demo bootstrap or reconciliation failed"
    )
    Invoke-Checked $powerShellCommand $revocationArguments (
        "Demo assignment revocation lifecycle probe failed"
    )
    $demoContract = Get-Content -LiteralPath (
        Join-Path $repositoryRoot "deploy/demo/demo-tenant.json"
    ) -Raw | ConvertFrom-Json
    $env:AGRIINSIGHT_ANALYTICS_ARTIFACT_ROOT = Join-Path (
        $repositoryRoot
    ) "artifacts/big-data"
    $env:AGRIINSIGHT_ANALYTICS_DEMO_TENANT_ID = [string]$demoContract.tenant.id
    $env:AGRIINSIGHT_ANALYTICS_RECONCILIATION_REPORT = Join-Path (
        $temporaryRuntimeRoot
    ) "demo-bootstrap/reconciliation.json"
    $env:AGRIINSIGHT_ANALYTICS_SPRING_BASE_URL = "http://127.0.0.1:58081"
    $env:AGRIINSIGHT_ANALYTICS_BIND_HOST = "127.0.0.1"
    $env:AGRIINSIGHT_ANALYTICS_PORT = "58082"

    $env:PGPASSWORD = $operatorPassword
    Invoke-Checked "psql" @(
        "-X", "--no-psqlrc",
        "--host", "127.0.0.1",
        "--port", "55443",
        "--dbname", "agriinsight_demo",
        "--username", "postgres",
        "--set", "ON_ERROR_STOP=1",
        "--set", "web_migrator_password=$webMigratorPassword",
        "--set", "web_runtime_password=$webRuntimePassword",
        "--file", "web/db/bootstrap-roles.sql"
    ) "Web database role bootstrap failed"
    Invoke-Checked "npm" @("--prefix", "web", "run", "db:migrate") (
        "Web session schema migration failed"
    )
    Invoke-Checked "npm" @("--prefix", "web", "run", "db:validate") (
        "Web runtime schema validation failed"
    )

    $env:AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL = (
        "postgresql://postgres:{0}@{1}" -f
        $encodedOperatorPassword,
        $databaseOrigin
    )
    Invoke-Checked "npm" @("--prefix", "web", "run", "test:db") (
        "Web database privilege tests failed"
    )

    if (-not (Test-Path -LiteralPath $backendJar)) {
        throw "Backend JAR is missing; run the guarded backend package gate first."
    }
    $env:AGRIINSIGHT_DB_URL = "jdbc:postgresql://127.0.0.1:55443/agriinsight_demo"
    $env:AGRIINSIGHT_DB_RUNTIME_USERNAME = "agriinsight_runtime"
    $env:AGRIINSIGHT_DB_RUNTIME_PASSWORD = $backendRuntimePassword
    $env:AGRIINSIGHT_FLYWAY_ENABLED = "false"
    $env:AGRIINSIGHT_IDENTITY_ENABLED = "true"
    $env:AGRIINSIGHT_OIDC_ISSUER_URI = "http://localhost:58080/realms/agriinsight-demo"
    $env:AGRIINSIGHT_OIDC_JWK_SET_URI = ""
    $env:AGRIINSIGHT_OIDC_API_AUDIENCE = "agriinsight-api"
    $env:AGRIINSIGHT_OIDC_INTERACTIVE_CLIENT_ID = "agriinsight-web"
    $env:SERVER_PORT = "58081"
    $env:AGRIINSIGHT_SERVER_ADDRESS = "127.0.0.1"
    $backendProcess = Start-GuardedProcess `
        -FilePath $javaExecutable `
        -ArgumentList @("-jar", $backendJar) `
        -WorkingDirectory (Join-Path $repositoryRoot "backend") `
        -StandardOutput $backendLogPath `
        -StandardError (Join-Path $artifactRuntimeRoot "backend-error.log")
    Write-Output "BACKEND_HOST_START jar=$backendJar port=58081"
    try {
        Wait-OwnedHttpProcessReady `
            "http://127.0.0.1:58081/actuator/health/readiness" `
            $backendProcess `
            58081
    } catch {
        if (Test-Path -LiteralPath $backendLogPath) {
            Write-Output "--- backend.log (tail) ---"
            Get-Content -LiteralPath $backendLogPath -Tail 120
        }
        $backendErrorPath = Join-Path $artifactRuntimeRoot "backend-error.log"
        if (Test-Path -LiteralPath $backendErrorPath) {
            Write-Output "--- backend-error.log (tail) ---"
            Get-Content -LiteralPath $backendErrorPath -Tail 120
        }
        throw
    }
    Write-Output "BACKEND_HOST_READY port=58081"
    $sourcePath = Join-Path $repositoryRoot "src"
    if ([string]::IsNullOrWhiteSpace($previousPythonPath)) {
        $env:PYTHONPATH = $sourcePath
    } else {
        $env:PYTHONPATH = (
            $sourcePath + [IO.Path]::PathSeparator + $previousPythonPath
        )
    }
    $analyticsProcess = Start-GuardedProcess `
        -FilePath "python" `
        -ArgumentList @("-m", "agriinsight.analytics_api") `
        -WorkingDirectory $repositoryRoot `
        -StandardOutput $analyticsLogPath `
        -StandardError (
            Join-Path $artifactRuntimeRoot "analytics-error.log"
        )
    Write-Output "ANALYTICS_HOST_START module=agriinsight.analytics_api port=58082"
    try {
        Wait-OwnedHttpProcessReady `
            "http://127.0.0.1:58082/health/ready" `
            $analyticsProcess `
            58082
    } catch {
        if (Test-Path -LiteralPath $analyticsLogPath) {
            Write-Output "--- analytics.log (tail) ---"
            Get-Content -LiteralPath $analyticsLogPath -Tail 120
        }
        $analyticsErrorPath = Join-Path $artifactRuntimeRoot "analytics-error.log"
        if (Test-Path -LiteralPath $analyticsErrorPath) {
            Write-Output "--- analytics-error.log (tail) ---"
            Get-Content -LiteralPath $analyticsErrorPath -Tail 120
        }
        throw
    }
    Write-Output "ANALYTICS_HOST_READY port=58082"
    Write-Output "PLAYWRIGHT_E2E_START base_url=http://localhost:3100"
    try {
        Invoke-Checked "npm" @(
            "--prefix", "web", "run", "test:e2e", "--",
            "--grep-invert", "@authorization|@work"
        ) (
            "Real platform core browser E2E failed"
        )
        Invoke-Checked "npm" @(
            "--prefix", "web", "run", "test:e2e", "--",
            "--grep", "@authorization|@work"
        ) (
            "Real platform authorization/work browser E2E failed"
        )
    } catch {
        foreach ($logPath in @(
            $analyticsLogPath,
            (Join-Path $artifactRuntimeRoot "analytics-error.log"),
            $backendLogPath,
            (Join-Path $artifactRuntimeRoot "backend-error.log")
        )) {
            if (Test-Path -LiteralPath $logPath) {
                Write-Output "--- $(Split-Path -Leaf $logPath) (tail) ---"
                Get-Content -LiteralPath $logPath -Tail 120
            }
        }
        throw
    }
    Write-Output "PLAYWRIGHT_E2E=PASS"

    if ($CaptureMedia) {
        # Documentation captures run only on an already-passing stack, in their
        # own config, so they can never change the acceptance scenario count.
        Write-Output "MEDIA_CAPTURE_START"
        Invoke-Checked "npm" @(
            "--prefix", "web", "exec", "--",
            "playwright", "test", "--config", "playwright.capture.config.ts"
        ) "Documentation media capture failed"
        Write-Output "MEDIA_CAPTURE=PASS"
    }

    Invoke-WorkspaceDiskGuard
} catch {
    $runError = $_
} finally {
    if ($runnerMutexHeld) {
        Invoke-CleanupStep "analytics process cleanup" {
            Stop-OwnedProcess $analyticsProcess "Analytics"
        } $cleanupErrors
        Invoke-CleanupStep "backend process cleanup" {
            Stop-OwnedProcess $backendProcess "Backend"
        } $cleanupErrors
        Invoke-CleanupStep "Python path restoration" {
            $env:PYTHONPATH = $previousPythonPath
        } $cleanupErrors
        if ($composeEnvironmentReady) {
            Invoke-CleanupStep "Compose cleanup" {
                Invoke-Compose @(
                    "--profile", "backend", "down", "--remove-orphans"
                ) "Web E2E cleanup failed"
                Assert-E2eProjectStopped
            } $cleanupErrors
        }
        Invoke-CleanupStep "artifact runtime cleanup" {
            Assert-E2eProjectStopped
            Remove-SafeRuntimeDirectory $artifactRuntimeRoot (
                $artifactRuntimeParent
            )
        } $cleanupErrors
        Invoke-CleanupStep "temporary runtime cleanup" {
            Assert-E2eProjectStopped
            Remove-SafeRuntimeDirectory $temporaryRuntimeRoot (
                Join-Path $repositoryRoot "_tmp"
            )
        } $cleanupErrors
    }
    Invoke-CleanupStep "location restoration" {
        Pop-Location
    } $cleanupErrors
    Invoke-CleanupStep "runner mutex cleanup" {
        if ($runnerMutexHeld) {
            $runnerMutex.ReleaseMutex()
            $runnerMutexHeld = $false
        }
        if ($null -ne $runnerMutex) {
            $runnerMutex.Dispose()
        }
    } $cleanupErrors
}
if ($null -ne $runError) {
    if ($cleanupErrors.Count -gt 0) {
        Write-Warning (
            "Web E2E failed and cleanup also reported: " +
            ($cleanupErrors -join "; ")
        )
    }
    throw $runError
}
if ($cleanupErrors.Count -gt 0) {
    throw "Web E2E cleanup failed: $($cleanupErrors -join '; ')"
}
Write-Output (
    "WEB_PLATFORM_E2E=PASS issuer=keycloak identity=spring-/me " +
    "session=postgres browser=chrome"
)
