[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$required = @(
    "AUTH_SPIKE_KEYCLOAK_ADMIN_USERNAME",
    "AUTH_SPIKE_KEYCLOAK_ADMIN_PASSWORD",
    "AUTH_SPIKE_OIDC_CLIENT_SECRET",
    "AUTH_SPIKE_TENANT_ADMIN_PASSWORD",
    "AUTH_SPIKE_EXECUTIVE_PASSWORD",
    "AUTH_SPIKE_FARM_MANAGER_PASSWORD",
    "AUTH_SPIKE_INVENTORY_MANAGER_PASSWORD",
    "AUTH_SPIKE_ANALYST_PASSWORD",
    "AUTH_SPIKE_FIELD_WORKER_PASSWORD",
    "AUTH_SPIKE_SUPPLIER_PASSWORD"
)

foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value.StartsWith("<")) {
        throw "$name must be supplied through the process environment"
    }
}

$container = if ($env:AUTH_SPIKE_KEYCLOAK_CONTAINER) {
    $env:AUTH_SPIKE_KEYCLOAK_CONTAINER
} else {
    (& docker compose -p agriinsight-auth-spike -f compose.auth-spike.yaml ps -q auth-spike-keycloak)
}
if ([string]::IsNullOrWhiteSpace($container)) {
    throw "Demo Keycloak container is not running"
}
$realm = "agriinsight-demo"

function Invoke-KeycloakAdmin {
    param(
        [Parameter(Mandatory = $true)] [string[]] $Arguments
    )
    $dockerArguments = @("exec")
    $dockerArguments += $container
    $dockerArguments += "/opt/keycloak/bin/kcadm.sh"
    $dockerArguments += $Arguments
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & docker @dockerArguments 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "Keycloak configuration command failed"
    }
    return $output
}

function Invoke-KeycloakSecretCommand {
    param(
        [Parameter(Mandatory = $true)] [string] $Command,
        [Parameter(Mandatory = $true)] [hashtable] $SecretEnvironment
    )
    $dockerArguments = @("exec")
    foreach ($entry in $SecretEnvironment.GetEnumerator()) {
        $dockerArguments += @("-e", "$($entry.Key)=$($entry.Value)")
    }
    $dockerArguments += @($container, "/bin/sh", "-ec", $Command)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker @dockerArguments 2>$null | Out-Null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "Keycloak secret injection command failed"
    }
}

Invoke-KeycloakSecretCommand -Command @'
/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_ADMIN_USER" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1
'@ -SecretEnvironment @{
    KC_ADMIN_USER = $env:AUTH_SPIKE_KEYCLOAK_ADMIN_USERNAME
    KC_ADMIN_PASSWORD = $env:AUTH_SPIKE_KEYCLOAK_ADMIN_PASSWORD
}

$clientRows = Invoke-KeycloakAdmin -Arguments @(
    "get", "clients", "-r", $realm, "-q", "clientId=agriinsight-web", "--fields", "id"
)
$client = ($clientRows -join "`n" | ConvertFrom-Json)
if ($client.Count -ne 1) { throw "Expected exactly one agriinsight-web client" }
$clientId = if ($client -is [array]) { $client[0].id } else { $client.id }

Invoke-KeycloakSecretCommand -Command @'
/opt/keycloak/bin/kcadm.sh update "clients/$KC_CLIENT_UUID" -r agriinsight-demo -s "secret=$KC_CLIENT_SECRET" >/dev/null 2>&1
'@ -SecretEnvironment @{
    KC_CLIENT_UUID = $clientId
    KC_CLIENT_SECRET = $env:AUTH_SPIKE_OIDC_CLIENT_SECRET
}

$personas = @{
    "tenant-admin" = $env:AUTH_SPIKE_TENANT_ADMIN_PASSWORD
    "executive" = $env:AUTH_SPIKE_EXECUTIVE_PASSWORD
    "farm-manager" = $env:AUTH_SPIKE_FARM_MANAGER_PASSWORD
    "inventory-manager" = $env:AUTH_SPIKE_INVENTORY_MANAGER_PASSWORD
    "analyst" = $env:AUTH_SPIKE_ANALYST_PASSWORD
    "field-worker" = $env:AUTH_SPIKE_FIELD_WORKER_PASSWORD
    "supplier" = $env:AUTH_SPIKE_SUPPLIER_PASSWORD
}

foreach ($persona in $personas.GetEnumerator()) {
    $userRows = Invoke-KeycloakAdmin -Arguments @(
        "get", "users", "-r", $realm, "-q", "username=$($persona.Key)", "--fields", "id"
    )
    $user = ($userRows -join "`n" | ConvertFrom-Json)
    if ($user.Count -ne 1) { throw "Expected exactly one stable user for $($persona.Key)" }
    $userId = if ($user -is [array]) { $user[0].id } else { $user.id }
    Invoke-KeycloakSecretCommand -Command @'
/opt/keycloak/bin/kcadm.sh set-password -r agriinsight-demo --userid "$KC_USER_UUID" --new-password "$KC_PERSONA_PASSWORD" >/dev/null 2>&1
'@ -SecretEnvironment @{
        KC_USER_UUID = $userId
        KC_PERSONA_PASSWORD = $persona.Value
    }
}

$issuerPort = if ($env:AUTH_SPIKE_KEYCLOAK_PORT) { $env:AUTH_SPIKE_KEYCLOAK_PORT } else { "58080" }
$expectedIssuer = "http://localhost:$issuerPort/realms/$realm"
$discovery = Invoke-RestMethod -Uri "$expectedIssuer/.well-known/openid-configuration" -TimeoutSec 10
if ($discovery.issuer -ne $expectedIssuer) { throw "Discovery issuer mismatch" }
foreach ($property in @("authorization_endpoint", "token_endpoint", "jwks_uri", "revocation_endpoint", "end_session_endpoint")) {
    if ([string]::IsNullOrWhiteSpace($discovery.$property)) { throw "Discovery omitted $property" }
}
if ($discovery.code_challenge_methods_supported -notcontains "S256") {
    throw "Issuer does not advertise PKCE S256"
}

$clientDetails = Invoke-KeycloakAdmin -Arguments @("get", "clients/$clientId", "-r", $realm)
$clientConfig = ($clientDetails -join "`n" | ConvertFrom-Json)
if ($clientConfig.publicClient -or -not $clientConfig.standardFlowEnabled -or
    $clientConfig.implicitFlowEnabled -or $clientConfig.directAccessGrantsEnabled -or
    $clientConfig.serviceAccountsEnabled) {
    throw "Client grant or confidentiality settings do not match the frozen contract"
}
$audienceMapper = $clientConfig.protocolMappers | Where-Object {
    $_.protocolMapper -eq "oidc-audience-mapper" -and $_.config."included.client.audience" -eq "agriinsight-api"
}
$tokenUseMapper = $clientConfig.protocolMappers | Where-Object {
    $_.protocolMapper -eq "oidc-hardcoded-claim-mapper" -and
    $_.config."claim.name" -eq "token_use" -and $_.config."claim.value" -eq "access"
}
if (-not $audienceMapper -or -not $tokenUseMapper) {
    throw "Access-token audience/token_use claim shape is not configured"
}

Write-Output "OIDC_DEMO_CONFIGURED issuer=exact pkce=S256 personas=7 claims=aud+token_use credentials=environment-only"
