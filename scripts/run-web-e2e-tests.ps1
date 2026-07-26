[CmdletBinding()]
param(
    [switch] $SkipStaticGates
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$projectName = "agriinsight-web-e2e"
$artifactRuntimeRoot = Join-Path $repositoryRoot "artifacts\_tmp\web-e2e"
$temporaryRuntimeRoot = Join-Path $repositoryRoot "_tmp\web-e2e"
$postgresRuntimeRoot = Join-Path $artifactRuntimeRoot "postgres"
$composeEnvironmentReady = $false
$backendProcess = $null
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

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)] [string] $Uri,
        [int] $Attempts = 60
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt += 1) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -TimeoutSec 3 -UseBasicParsing
            if ($response.StatusCode -eq 200) { return }
        } catch {
            if ($attempt -eq $Attempts) {
                throw "Timed out waiting for $Uri"
            }
        }
        Start-Sleep -Seconds 2
    }
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

function Stop-BackendProcess {
    param(
        [Parameter(Mandatory = $true)] [string] $JarPath,
        [System.Diagnostics.Process] $LauncherProcess
    )
    if ($null -ne $LauncherProcess -and -not $LauncherProcess.HasExited) {
        Stop-Process -Id $LauncherProcess.Id -Force
        [void] $LauncherProcess.WaitForExit(10000)
    }
    $expectedCommandFragment = [IO.Path]::GetFullPath($JarPath)
    $backendProcesses = @(
        Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
            Where-Object {
                $_.CommandLine -and
                $_.CommandLine.IndexOf(
                    $expectedCommandFragment,
                    [StringComparison]::OrdinalIgnoreCase
                ) -ge 0
            }
    )
    foreach ($process in $backendProcesses) {
        Stop-Process -Id $process.ProcessId -Force
    }
    for ($attempt = 1; $attempt -le 20; $attempt += 1) {
        $remaining = @(
            Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
                Where-Object {
                    $_.CommandLine -and
                    $_.CommandLine.IndexOf(
                        $expectedCommandFragment,
                        [StringComparison]::OrdinalIgnoreCase
                    ) -ge 0
                }
        )
        if ($remaining.Count -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Backend E2E process did not stop"
}

Push-Location $repositoryRoot
$backendJar = Join-Path $repositoryRoot "backend\target\agriinsight-backend-0.1.0-SNAPSHOT.jar"
try {
    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/check-workspace-disk.ps1"
    ) "Workspace disk guard failed before web E2E"

    if ((& node --version) -ne "v24.12.0") {
        throw "Web E2E requires Node v24.12.0"
    }
    if ((& npm --version) -ne "11.6.2") {
        throw "Web E2E requires npm 11.6.2"
    }
    Invoke-Checked "docker" @("info") "Docker daemon is required for web E2E"

    $operatorPassword = New-HexSecret
    $backendMigratorPassword = New-HexSecret
    $backendRuntimePassword = New-HexSecret
    $webMigratorPassword = New-HexSecret
    $webRuntimePassword = New-HexSecret
    $clientSecret = New-HexSecret
    $personaPassword = New-HexSecret 16

    $env:AGRIINSIGHT_DEMO_POSTGRES_DATA_DIR = "./artifacts/_tmp/web-e2e/postgres"
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
        Join-Path $repositoryRoot "artifacts\_tmp"
    )
    Remove-SafeRuntimeDirectory $temporaryRuntimeRoot (
        Join-Path $repositoryRoot "_tmp"
    )
    New-Item -ItemType Directory -Force -Path (
        Join-Path $artifactRuntimeRoot "npm-cache"
    ), (Join-Path $artifactRuntimeRoot "temp"), $postgresRuntimeRoot | Out-Null
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
    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/configure-demo-oidc.ps1"
    ) "Demo OIDC configuration failed"

    $env:PGPASSWORD = $backendMigratorPassword
    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass",
        "-File", "scripts/bootstrap-demo-environment.ps1",
        "-ConfirmLocalDemo",
        "-DatabasePort", "55443",
        "-OutputDirectory", "_tmp/web-e2e/demo-bootstrap"
    ) "Guarded demo bootstrap or reconciliation failed"

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
    $backendProcess = Start-Process `
        -FilePath "java" `
        -ArgumentList @("-jar", $backendJar) `
        -WorkingDirectory (Join-Path $repositoryRoot "backend") `
        -WindowStyle Hidden `
        -RedirectStandardOutput $backendLogPath `
        -RedirectStandardError (Join-Path $artifactRuntimeRoot "backend-error.log") `
        -PassThru
    Write-Output "BACKEND_HOST_START jar=$backendJar port=58081"
    try {
        Wait-HttpReady "http://127.0.0.1:58081/actuator/health/readiness"
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
    Write-Output "PLAYWRIGHT_E2E_START base_url=http://localhost:3100"
    Invoke-Checked "npm" @("--prefix", "web", "run", "test:e2e") (
        "Real Keycloak/PostgreSQL/Spring/Chrome web E2E failed"
    )
    Write-Output "PLAYWRIGHT_E2E=PASS"

    Invoke-Checked "powershell" @(
        "-ExecutionPolicy", "Bypass", "-File", "scripts/check-workspace-disk.ps1"
    ) "Workspace disk guard failed after web E2E"
    Write-Output (
        "WEB_PLATFORM_E2E=PASS issuer=keycloak identity=spring-/me " +
        "session=postgres browser=chrome"
    )
} finally {
    Stop-BackendProcess $backendJar $backendProcess
    if ($composeEnvironmentReady) {
        try {
            Invoke-Compose @(
                "--profile", "backend", "down", "--remove-orphans"
            ) "Web E2E cleanup failed"
        } catch {
            Write-Warning $_
        }
        Assert-E2eProjectStopped
    }
    Remove-SafeRuntimeDirectory $artifactRuntimeRoot (
        Join-Path $repositoryRoot "artifacts\_tmp"
    )
    Remove-SafeRuntimeDirectory $temporaryRuntimeRoot (
        Join-Path $repositoryRoot "_tmp"
    )
    Pop-Location
}
