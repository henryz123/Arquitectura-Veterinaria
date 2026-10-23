[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-LocalEnvironment {
    param([string]$EnvironmentFile)

    $result = @{}
    Get-Content -LiteralPath $EnvironmentFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $result[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $result
}

function ConvertTo-SqlLiteral {
    param([string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function New-Sha256Hex {
    param([string]$Value)

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return -join ($algorithm.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    } finally {
        $algorithm.Dispose()
    }
}

function Start-PostgresSql {
    param(
        [string]$PsqlPath,
        [string]$DatabaseUrl,
        [string]$Username,
        [string]$Password,
        [string]$Sql
    )

    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $PsqlPath
    $info.Arguments = "--no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align --username $Username --dbname $DatabaseUrl"
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['PGPASSWORD'] = $Password

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) {
        throw 'No se pudo iniciar psql para la auditoría de concurrencia.'
    }
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    return $process
}

function Complete-PostgresSql {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Operation
    )

    $stdout = $Process.StandardOutput.ReadToEnd()
    $stderr = $Process.StandardError.ReadToEnd()
    $Process.WaitForExit()
    if ($Process.ExitCode -ne 0) {
        throw "$Operation no se completó (psql exit $($Process.ExitCode))."
    }
    return $stdout
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$values = Get-LocalEnvironment (Join-Path $repositoryRoot '.env.local')
$required = 'AV_DB_URL', 'AV_DB_USER', 'AV_DB_PASSWORD', 'AV_MIGRATION_DB_USER', 'AV_MIGRATION_DB_PASSWORD'
$missing = $required | Where-Object { -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($values[$_]) }
if ($missing) {
    throw 'Faltan variables locales requeridas para la auditoría de concurrencia.'
}

$databaseUrl = $values['AV_DB_URL'] -replace '^jdbc:', ''
if ($databaseUrl -ne 'postgresql://localhost:5432/arquitectura_veterinaria' -or $values['AV_DB_USER'] -ne 'av_app' -or $values['AV_MIGRATION_DB_USER'] -ne 'av_schema_owner') {
    throw 'La auditoría sólo permite av_app y av_schema_owner sobre arquitectura_veterinaria local.'
}

$psqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
if (-not (Test-Path -LiteralPath $psqlPath)) {
    throw 'psql de PostgreSQL 18 no está disponible.'
}

$nonce = [Guid]::NewGuid().ToString('N')
$tenantId = [Guid]::NewGuid().ToString()
$tenantSlug = "g4-token-concurrency-$($nonce.Substring(0, 12))"
$syntheticEmail = "g4-token-$($nonce.Substring(0, 16))@synthetic.invalid"
$verificationHash = New-Sha256Hex "verification-$nonce"
$completionHash = New-Sha256Hex "completion-$nonce"
$recoveryHashA = New-Sha256Hex "recovery-a-$nonce"
$recoveryHashB = New-Sha256Hex "recovery-b-$nonce"
$tenantLiteral = ConvertTo-SqlLiteral $tenantId
$slugLiteral = ConvertTo-SqlLiteral $tenantSlug
$emailLiteral = ConvertTo-SqlLiteral $syntheticEmail
$verificationLiteral = ConvertTo-SqlLiteral $verificationHash
$completionLiteral = ConvertTo-SqlLiteral $completionHash
$recoveryLiteralA = ConvertTo-SqlLiteral $recoveryHashA
$recoveryLiteralB = ConvertTo-SqlLiteral $recoveryHashB
$principalId = $null
$fixtureCreated = $false
$cleanupConfirmed = $false

try {
    $fixtureSql = @"
begin;
insert into tenancy.tenant (id, slug, commercial_name, status)
values ($tenantLiteral, $slugLiteral, 'G4 Token Concurrency Isolation', 'ACTIVO');
commit;
select 'FIXTURE_CREATED';
"@
    $fixture = Start-PostgresSql $psqlPath $databaseUrl $values['AV_MIGRATION_DB_USER'] $values['AV_MIGRATION_DB_PASSWORD'] $fixtureSql
    if ((Complete-PostgresSql $fixture 'La creación de fixture aislado') -notmatch 'FIXTURE_CREATED') {
        throw 'No se confirmó el fixture aislado.'
    }
    $fixtureCreated = $true

    $registrationSql = @"
select case when identity_auth.register_client(
    $slugLiteral,
    $emailLiteral,
    'g4-i01-synthetic-password-hash',
    $verificationLiteral,
    statement_timestamp() + interval '23 hours'
) is not null then 'CLIENT_REGISTERED' else 'CLIENT_NOT_REGISTERED' end;
select case when exists (
    select 1
    from identity_auth.verify_email_link(
        $verificationLiteral,
        $completionLiteral,
        statement_timestamp() + interval '15 minutes'
    )
    where verification_status = 'ACTIVA' and verified_account_type = 'CLIENT'
) then 'CLIENT_VERIFIED' else 'CLIENT_NOT_VERIFIED' end;
"@
    $registration = Start-PostgresSql $psqlPath $databaseUrl $values['AV_DB_USER'] $values['AV_DB_PASSWORD'] $registrationSql
    $registrationOutput = Complete-PostgresSql $registration 'El registro del cliente sintético'
    if ($registrationOutput -notmatch 'CLIENT_REGISTERED' -or $registrationOutput -notmatch 'CLIENT_VERIFIED') {
        throw 'No se pudo preparar el cliente sintético aislado.'
    }

    $principalLookupSql = @"
select principal_id
from identity_auth.account_profile
where tenant_id = $tenantLiteral::uuid and account_type = 'CLIENT' and normalized_email = $emailLiteral;
"@
    $principalLookup = Start-PostgresSql $psqlPath $databaseUrl $values['AV_MIGRATION_DB_USER'] $values['AV_MIGRATION_DB_PASSWORD'] $principalLookupSql
    $principalId = (Complete-PostgresSql $principalLookup 'La identificación del cliente sintético').Trim()
    if ($principalId -notmatch '^[0-9a-fA-F-]{36}$') {
        throw 'No se pudo identificar el principal sintético para limpieza.'
    }
    $principalLiteral = ConvertTo-SqlLiteral $principalId

    $requestTemplate = {
        param([string]$RecoveryLiteral)
@"
begin;
select case when count(*) = 1 then 'RECOVERY_REQUESTED' else 'RECOVERY_REJECTED' end
from identity_auth.request_password_recovery('CLIENT', $slugLiteral, $emailLiteral, $RecoveryLiteral, statement_timestamp() + interval '29 minutes');
select pg_sleep(2);
commit;
select 'RECOVERY_COMMITTED';
"@
    }
    $requestA = Start-PostgresSql $psqlPath $databaseUrl $values['AV_DB_USER'] $values['AV_DB_PASSWORD'] (& $requestTemplate $recoveryLiteralA)
    $requestB = Start-PostgresSql $psqlPath $databaseUrl $values['AV_DB_USER'] $values['AV_DB_PASSWORD'] (& $requestTemplate $recoveryLiteralB)
    $requestAOutput = Complete-PostgresSql $requestA 'La primera solicitud concurrente'
    $requestBOutput = Complete-PostgresSql $requestB 'La segunda solicitud concurrente'
    if ($requestAOutput -notmatch 'RECOVERY_REQUESTED|RECOVERY_REJECTED' -or $requestBOutput -notmatch 'RECOVERY_REQUESTED|RECOVERY_REJECTED') {
        throw 'Las solicitudes concurrentes no devolvieron un resultado verificable.'
    }

    $activeSql = @"
select count(*)
from identity_auth.one_time_token
where purpose = 'PASSWORD_RECOVERY' and principal_id = $principalLiteral::uuid
  and consumed_at is null and invalidated_at is null and expires_at > statement_timestamp();
"@
    $activeLookup = Start-PostgresSql $psqlPath $databaseUrl $values['AV_MIGRATION_DB_USER'] $values['AV_MIGRATION_DB_PASSWORD'] $activeSql
    $activeCount = [int](Complete-PostgresSql $activeLookup 'El conteo de tokens activos').Trim()

    $resetASql = "select identity_auth.reset_password_with_recovery($recoveryLiteralA, 'g4-i01-synthetic-reset-a');"
    $resetBSql = "select identity_auth.reset_password_with_recovery($recoveryLiteralB, 'g4-i01-synthetic-reset-b');"
    $resetA = Start-PostgresSql $psqlPath $databaseUrl $values['AV_DB_USER'] $values['AV_DB_PASSWORD'] $resetASql
    $resetAOutput = Complete-PostgresSql $resetA 'El consumo del primer token de recuperación'
    $resetB = Start-PostgresSql $psqlPath $databaseUrl $values['AV_DB_USER'] $values['AV_DB_PASSWORD'] $resetBSql
    $resetBOutput = Complete-PostgresSql $resetB 'El consumo del segundo token de recuperación'
    $usableCount = 0
    foreach ($resetOutput in @($resetAOutput, $resetBOutput)) {
        if ($resetOutput -match '(?m)^t\s*$') {
            $usableCount++
        }
    }

    if ($activeCount -ge 2 -and $usableCount -eq 2) {
        $outcome = 'DEFECT_REPRODUCED'
    } elseif ($activeCount -le 1 -and $usableCount -le 1) {
        $outcome = 'CONTROL_EFFECTIVE'
    } else {
        $outcome = 'INCONCLUSIVE'
    }
} finally {
    if ($fixtureCreated) {
        $principalPredicate = if ($principalId) { "or related_resource_id = $(ConvertTo-SqlLiteral $principalId)::uuid" } else { '' }
        $cleanupSql = @"
begin;
delete from tenant_admin.notification_outbox where false $principalPredicate;
delete from tenant_admin.audit_entry where tenant_id = $tenantLiteral::uuid or actor_principal_id = $(if ($principalId) { (ConvertTo-SqlLiteral $principalId) + '::uuid' } else { 'null' });
delete from identity_auth.one_time_token where tenant_id = $tenantLiteral::uuid $(if ($principalId) { "or principal_id = $(ConvertTo-SqlLiteral $principalId)::uuid" } else { '' });
delete from identity_auth.account_profile where tenant_id = $tenantLiteral::uuid;
delete from identity_auth.principal where id = $(if ($principalId) { (ConvertTo-SqlLiteral $principalId) + '::uuid' } else { 'null' });
delete from tenancy.tenant where id = $tenantLiteral::uuid;
commit;
select case when not exists (select 1 from tenancy.tenant where id = $tenantLiteral::uuid) then 'CLEANUP_CONFIRMED' else 'CLEANUP_INCOMPLETE' end;
"@
        $cleanup = Start-PostgresSql $psqlPath $databaseUrl $values['AV_MIGRATION_DB_USER'] $values['AV_MIGRATION_DB_PASSWORD'] $cleanupSql
        $cleanupConfirmed = (Complete-PostgresSql $cleanup 'La limpieza del fixture aislado') -match 'CLEANUP_CONFIRMED'
    }
}

if (-not $cleanupConfirmed) {
    throw 'La limpieza del fixture sintético no pudo confirmarse.'
}

Write-Output "G4_I01_TOKEN_CONCURRENCY=$outcome;ACTIVE_TOKENS=$activeCount;SEQUENTIALLY_USABLE=$usableCount;CLEANUP=PASS"
