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
        [string]$Sql,
        [bool]$StopOnError = $true
    )

    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $PsqlPath
    $info.Arguments = "--no-psqlrc --tuples-only --no-align --username $Username --dbname $DatabaseUrl"
    if ($StopOnError) {
        $info.Arguments = "--set ON_ERROR_STOP=1 $($info.Arguments)"
    }
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['PGPASSWORD'] = $Password

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) {
        throw 'No se pudo iniciar psql para la verificaciÃ³n V011.'
    }
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    return $process
}

function Complete-PostgresSql {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Operation,
        [bool]$AllowSqlError = $false
    )

    $stdout = $Process.StandardOutput.ReadToEnd()
    $stderr = $Process.StandardError.ReadToEnd()
    $Process.WaitForExit()
    if ($Process.ExitCode -ne 0 -and -not $AllowSqlError) {
        throw "$Operation no se completÃ³ (psql exit $($Process.ExitCode))."
    }
    return [pscustomobject]@{
        ExitCode = $Process.ExitCode
        Output = $stdout
        Error = $stderr
        Combined = "$stdout`n$stderr"
    }
}

function Invoke-PostgresSql {
    param(
        [string]$Role,
        [string]$Sql,
        [string]$Operation,
        [bool]$StopOnError = $true,
        [bool]$AllowSqlError = $false
    )

    $passwordKey = if ($Role -eq 'av_app') { 'AV_DB_PASSWORD' } else { 'AV_MIGRATION_DB_PASSWORD' }
    $process = Start-PostgresSql $script:PsqlPath $script:DatabaseUrl $Role $script:Values[$passwordKey] $Sql $StopOnError
    return Complete-PostgresSql $process $Operation $AllowSqlError
}

function Assert-Condition {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

function Get-PrincipalId {
    param([string]$TenantId, [string]$Email)

    $tenantLiteral = ConvertTo-SqlLiteral $TenantId
    $emailLiteral = ConvertTo-SqlLiteral $Email
    $result = Invoke-PostgresSql 'av_schema_owner' @"
select principal_id
from identity_auth.account_profile
where tenant_id = $tenantLiteral::uuid
  and account_type = 'CLIENT'
  and normalized_email = $emailLiteral;
"@ 'La identificaciÃ³n del principal sintÃ©tico'
    $id = $result.Output.Trim()
    Assert-Condition ($id -match '^[0-9a-fA-F-]{36}$') 'No se pudo identificar un principal sintÃ©tico.'
    return $id
}

function Register-VerifiedClient {
    param([string]$TenantSlug, [string]$Email, [string]$Nonce)

    $slugLiteral = ConvertTo-SqlLiteral $TenantSlug
    $emailLiteral = ConvertTo-SqlLiteral $Email
    $verificationLiteral = ConvertTo-SqlLiteral (New-Sha256Hex "v011-verification-$Nonce")
    $completionLiteral = ConvertTo-SqlLiteral (New-Sha256Hex "v011-completion-$Nonce")
    $result = Invoke-PostgresSql 'av_app' @"
select case when identity_auth.register_client(
    $slugLiteral,
    $emailLiteral,
    'v011-synthetic-password-hash',
    $verificationLiteral,
    statement_timestamp() + interval '23 hours'
) is not null then 'CLIENT_REGISTERED' else 'CLIENT_NOT_REGISTERED' end;
select case when exists (
    select 1 from identity_auth.verify_email_link(
        $verificationLiteral,
        $completionLiteral,
        statement_timestamp() + interval '15 minutes'
    ) where verification_status = 'ACTIVA' and verified_account_type = 'CLIENT'
) then 'CLIENT_VERIFIED' else 'CLIENT_NOT_VERIFIED' end;
"@ 'El registro del cliente sintÃ©tico'
    Assert-Condition ($result.Output -match 'CLIENT_REGISTERED') 'No se registrÃ³ el cliente sintÃ©tico.'
    Assert-Condition ($result.Output -match 'CLIENT_VERIFIED') 'No se verificÃ³ el cliente sintÃ©tico.'
}

function Start-RecoveryRequest {
    param(
        [string]$TenantSlug,
        [string]$Email,
        [string]$Hash,
        [bool]$Commit = $true
    )

    $slugLiteral = ConvertTo-SqlLiteral $TenantSlug
    $emailLiteral = ConvertTo-SqlLiteral $Email
    $hashLiteral = ConvertTo-SqlLiteral $Hash
    $finish = if ($Commit) { 'commit;' } else { 'rollback;' }
    $marker = if ($Commit) { 'RECOVERY_COMMITTED' } else { 'RECOVERY_ROLLED_BACK' }
    return Start-PostgresSql $script:PsqlPath $script:DatabaseUrl 'av_app' $script:Values['AV_DB_PASSWORD'] @"
begin;
select case when count(*) = 1 then 'RECOVERY_REQUESTED' else 'RECOVERY_REJECTED' end
from identity_auth.request_password_recovery(
    'CLIENT', $slugLiteral, $emailLiteral, $hashLiteral, statement_timestamp() + interval '29 minutes'
);
select pg_sleep(1);
$finish
select '$marker';
"@
}

function Complete-RecoveryRequests {
    param([System.Diagnostics.Process[]]$Processes, [string]$Operation)

    $results = @($Processes | ForEach-Object { Complete-PostgresSql $_ $Operation })
    foreach ($result in $results) {
        Assert-Condition ($result.Output -match 'RECOVERY_REQUESTED') "$Operation no emitiÃ³ un enlace verificable."
        Assert-Condition ($result.Output -match 'RECOVERY_COMMITTED') "$Operation no confirmÃ³ la transacciÃ³n."
    }
    return $results
}

function Get-ActiveRecoveryCount {
    param([string]$PrincipalId)

    $principalLiteral = ConvertTo-SqlLiteral $PrincipalId
    $result = Invoke-PostgresSql 'av_schema_owner' @"
select count(*)
from identity_auth.one_time_token token
where token.purpose = 'PASSWORD_RECOVERY'
  and token.principal_id = $principalLiteral::uuid
  and token.consumed_at is null
  and token.invalidated_at is null
  and token.expires_at > statement_timestamp();
"@ 'El conteo de enlaces activos'
    return [int]$result.Output.Trim()
}

function Invoke-ResetOutcome {
    param([string]$Hash)

    $hashLiteral = ConvertTo-SqlLiteral $Hash
    $result = Invoke-PostgresSql 'av_app' @"
do `$`$
begin
    begin
        if identity_auth.reset_password_with_recovery($hashLiteral, 'v011-synthetic-password-reset') then
            raise notice 'RESET_OK';
        end if;
    exception when sqlstate '22023' then
        raise notice 'RESET_REJECTED';
    end;
end;
`$`$;
"@ 'El consumo controlado de enlace'
    if ($result.Combined -match 'RESET_OK') {
        return 'OK'
    }
    if ($result.Combined -match 'RESET_REJECTED') {
        return 'REJECTED'
    }
    throw 'El consumo controlado no produjo un resultado verificable.'
}

function Invoke-RecoveryWithDuplicateHashRollback {
    param([string]$TenantSlug, [string]$Email, [string]$Hash)

    $slugLiteral = ConvertTo-SqlLiteral $TenantSlug
    $emailLiteral = ConvertTo-SqlLiteral $Email
    $hashLiteral = ConvertTo-SqlLiteral $Hash
    $result = Invoke-PostgresSql 'av_app' @"
begin;
select * from identity_auth.request_password_recovery(
    'CLIENT', $slugLiteral, $emailLiteral, $hashLiteral, statement_timestamp() + interval '29 minutes'
);
rollback;
select 'DUPLICATE_INSERT_ROLLED_BACK';
"@ 'El rollback tras colisiÃ³n de token' $false $true
    Assert-Condition ($result.Combined -match 'duplicate key|duplicada') 'No se reprodujo la colisiÃ³n de hash controlada.'
    Assert-Condition ($result.Output -match 'DUPLICATE_INSERT_ROLLED_BACK') 'El rollback tras la colisiÃ³n no se completÃ³.'
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$script:Values = Get-LocalEnvironment (Join-Path $repositoryRoot '.env.local')
$required = 'AV_DB_URL', 'AV_DB_USER', 'AV_DB_PASSWORD', 'AV_MIGRATION_DB_USER', 'AV_MIGRATION_DB_PASSWORD'
$missing = $required | Where-Object { -not $script:Values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($script:Values[$_]) }
if ($missing) {
    throw 'Faltan variables locales requeridas para la verificaciÃ³n V011.'
}

$script:DatabaseUrl = $script:Values['AV_DB_URL'] -replace '^jdbc:', ''
if ($script:DatabaseUrl -ne 'postgresql://localhost:5432/arquitectura_veterinaria' -or $script:Values['AV_DB_USER'] -ne 'av_app' -or $script:Values['AV_MIGRATION_DB_USER'] -ne 'av_schema_owner') {
    throw 'La verificaciÃ³n V011 sÃ³lo permite los roles autorizados sobre arquitectura_veterinaria local.'
}

$script:PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
if (-not (Test-Path -LiteralPath $script:PsqlPath)) {
    throw 'psql de PostgreSQL 18 no estÃ¡ disponible.'
}

$nonce = [Guid]::NewGuid().ToString('N')
$tenantAuroraId = [Guid]::NewGuid().ToString()
$tenantBosqueId = [Guid]::NewGuid().ToString()
$tenantAuroraSlug = "g4-v011-aurora-$($nonce.Substring(0, 10))"
$tenantBosqueSlug = "g4-v011-bosque-$($nonce.Substring(0, 10))"
$sharedEmail = "g4-v011-shared-$($nonce.Substring(0, 14))@synthetic.invalid"
$secondaryEmail = "g4-v011-secondary-$($nonce.Substring(0, 14))@synthetic.invalid"
$principalIds = @()
$fixtureCreated = $false
$cleanupConfirmed = $false

try {
    $tenantAuroraLiteral = ConvertTo-SqlLiteral $tenantAuroraId
    $tenantBosqueLiteral = ConvertTo-SqlLiteral $tenantBosqueId
    $fixture = Invoke-PostgresSql 'av_schema_owner' @"
begin;
insert into tenancy.tenant (id, slug, commercial_name, status) values
($tenantAuroraLiteral, $(ConvertTo-SqlLiteral $tenantAuroraSlug), 'G4 V011 Aurora Isolation', 'ACTIVO'),
($tenantBosqueLiteral, $(ConvertTo-SqlLiteral $tenantBosqueSlug), 'G4 V011 Bosque Isolation', 'ACTIVO');
commit;
select 'FIXTURE_CREATED';
"@ 'La creaciÃ³n de tenants sintÃ©ticos'
    Assert-Condition ($fixture.Output -match 'FIXTURE_CREATED') 'No se creÃ³ el fixture sintÃ©tico.'
    $fixtureCreated = $true

    Register-VerifiedClient $tenantAuroraSlug $sharedEmail "$nonce-aurora-shared"
    Register-VerifiedClient $tenantAuroraSlug $secondaryEmail "$nonce-aurora-secondary"
    Register-VerifiedClient $tenantBosqueSlug $sharedEmail "$nonce-bosque-shared"

    $auroraSharedPrincipal = Get-PrincipalId $tenantAuroraId $sharedEmail
    $auroraSecondaryPrincipal = Get-PrincipalId $tenantAuroraId $secondaryEmail
    $bosqueSharedPrincipal = Get-PrincipalId $tenantBosqueId $sharedEmail
    $principalIds = @($auroraSharedPrincipal, $auroraSecondaryPrincipal, $bosqueSharedPrincipal)

    $twoHashes = 1..2 | ForEach-Object { New-Sha256Hex "v011-two-$nonce-$_" }
    Complete-RecoveryRequests @($twoHashes | ForEach-Object { Start-RecoveryRequest $tenantAuroraSlug $sharedEmail $_ }) 'Dos COMMIT concurrentes' | Out-Null
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSharedPrincipal) -eq 1) 'Dos COMMIT dejaron mÃ¡s de un enlace activo.'
    $twoOutcomes = @($twoHashes | ForEach-Object { Invoke-ResetOutcome $_ })
    Assert-Condition ((@($twoOutcomes | Where-Object { $_ -eq 'OK' }).Count) -eq 1) 'Dos enlaces concurrentes fueron utilizables.'

    $fiveHashes = 1..5 | ForEach-Object { New-Sha256Hex "v011-five-$nonce-$_" }
    Complete-RecoveryRequests @($fiveHashes | ForEach-Object { Start-RecoveryRequest $tenantAuroraSlug $sharedEmail $_ }) 'Cinco COMMIT concurrentes' | Out-Null
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSharedPrincipal) -eq 1) 'Cinco COMMIT dejaron mÃ¡s de un enlace activo.'
    $fiveOutcomes = @($fiveHashes | ForEach-Object { Invoke-ResetOutcome $_ })
    Assert-Condition ((@($fiveOutcomes | Where-Object { $_ -eq 'OK' }).Count) -eq 1) 'Cinco enlaces concurrentes fueron utilizables.'

    $differentHashes = @(
        (New-Sha256Hex "v011-different-a-$nonce")
        (New-Sha256Hex "v011-different-b-$nonce")
    )
    $differentProcesses = @(
        (Start-RecoveryRequest -TenantSlug $tenantAuroraSlug -Email $sharedEmail -Hash $differentHashes[0]),
        (Start-RecoveryRequest -TenantSlug $tenantAuroraSlug -Email $secondaryEmail -Hash $differentHashes[1])
    )
    Complete-RecoveryRequests $differentProcesses 'Principales distintos concurrentes' | Out-Null
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSharedPrincipal) -eq 1) 'El principal Aurora compartido no conservÃ³ un Ãºnico enlace activo.'
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSecondaryPrincipal) -eq 1) 'El principal Aurora secundario no conservÃ³ un Ãºnico enlace activo.'

    $tenantHashes = @(
        (New-Sha256Hex "v011-tenant-a-$nonce")
        (New-Sha256Hex "v011-tenant-b-$nonce")
    )
    $tenantProcesses = @(
        (Start-RecoveryRequest -TenantSlug $tenantAuroraSlug -Email $sharedEmail -Hash $tenantHashes[0]),
        (Start-RecoveryRequest -TenantSlug $tenantBosqueSlug -Email $sharedEmail -Hash $tenantHashes[1])
    )
    Complete-RecoveryRequests $tenantProcesses 'Aurora y Bosque sintÃ©ticos concurrentes' | Out-Null
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSharedPrincipal) -eq 1) 'Aurora sintÃ©tico perdiÃ³ aislamiento de recuperaciÃ³n.'
    Assert-Condition ((Get-ActiveRecoveryCount $bosqueSharedPrincipal) -eq 1) 'Bosque sintÃ©tico perdiÃ³ aislamiento de recuperaciÃ³n.'

    $rollbackOld = New-Sha256Hex "v011-rollback-old-$nonce"
    Complete-RecoveryRequests @(Start-RecoveryRequest $tenantAuroraSlug $secondaryEmail $rollbackOld) 'EmisiÃ³n previa para rollback' | Out-Null
    $rollbackNew = New-Sha256Hex "v011-rollback-new-$nonce"
    $rollbackResult = Complete-PostgresSql (Start-RecoveryRequest $tenantAuroraSlug $secondaryEmail $rollbackNew $false) 'Solicitud con rollback'
    Assert-Condition ($rollbackResult.Output -match 'RECOVERY_REQUESTED') 'La solicitud que debÃ­a revertirse no se ejecutÃ³.'
    Assert-Condition ($rollbackResult.Output -match 'RECOVERY_ROLLED_BACK') 'La solicitud no terminÃ³ en ROLLBACK.'
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSecondaryPrincipal) -eq 1) 'El rollback alterÃ³ el enlace activo previo.'
    Assert-Condition ((Invoke-ResetOutcome $rollbackOld) -eq 'OK') 'El enlace previo no sobreviviÃ³ al rollback.'

    $duplicateHash = New-Sha256Hex "v011-duplicate-$nonce"
    Complete-RecoveryRequests @(Start-RecoveryRequest $tenantBosqueSlug $sharedEmail $duplicateHash) 'EmisiÃ³n previa para error controlado' | Out-Null
    Invoke-RecoveryWithDuplicateHashRollback $tenantBosqueSlug $sharedEmail $duplicateHash
    Assert-Condition ((Get-ActiveRecoveryCount $bosqueSharedPrincipal) -eq 1) 'El error de inserciÃ³n no revirtiÃ³ la invalidaciÃ³n previa.'
    Assert-Condition ((Invoke-ResetOutcome $duplicateHash) -eq 'OK') 'El enlace previo no sobreviviÃ³ al error controlado.'

    $oldHash = New-Sha256Hex "v011-old-$nonce"
    Complete-RecoveryRequests @(Start-RecoveryRequest $tenantAuroraSlug $sharedEmail $oldHash) 'EmisiÃ³n de enlace anterior' | Out-Null
    $newHash = New-Sha256Hex "v011-new-$nonce"
    Complete-RecoveryRequests @(Start-RecoveryRequest $tenantAuroraSlug $sharedEmail $newHash) 'EmisiÃ³n de enlace nuevo' | Out-Null
    Assert-Condition ((Invoke-ResetOutcome $oldHash) -eq 'REJECTED') 'El enlace previo permaneciÃ³ utilizable tras emitir uno nuevo.'
    Assert-Condition ((Invoke-ResetOutcome $newHash) -eq 'OK') 'El enlace nuevo no fue utilizable.'
    Assert-Condition ((Invoke-ResetOutcome $newHash) -eq 'REJECTED') 'Un enlace consumido pudo reutilizarse.'

    $expiryHash = New-Sha256Hex "v011-expiry-$nonce"
    $expiryLiteral = ConvertTo-SqlLiteral $expiryHash
    $exactExpiry = Invoke-PostgresSql 'av_app' @"
select count(*) from identity_auth.request_password_recovery(
    'CLIENT', $(ConvertTo-SqlLiteral $tenantAuroraSlug), $(ConvertTo-SqlLiteral $sharedEmail),
    $expiryLiteral, statement_timestamp() + interval '30 minutes'
);
"@ 'El lÃ­mite exacto de treinta minutos'
    Assert-Condition ($exactExpiry.Output.Trim() -eq '1') 'El lÃ­mite exacto de treinta minutos fue rechazado.'
    $tooLong = Invoke-PostgresSql 'av_app' @"
select count(*) from identity_auth.request_password_recovery(
    'CLIENT', $(ConvertTo-SqlLiteral $tenantAuroraSlug), $(ConvertTo-SqlLiteral $sharedEmail),
    $(ConvertTo-SqlLiteral (New-Sha256Hex "v011-too-long-$nonce")), statement_timestamp() + interval '30 minutes 1 microsecond'
);
"@ 'El rechazo por vigencia superior a treinta minutos'
    Assert-Condition ($tooLong.Output.Trim() -eq '0') 'Una vigencia superior a treinta minutos fue aceptada.'
    Invoke-PostgresSql 'av_schema_owner' @"
update identity_auth.one_time_token set expires_at = statement_timestamp() - interval '1 microsecond'
where token_hash = $expiryLiteral and principal_id = $(ConvertTo-SqlLiteral $auroraSharedPrincipal)::uuid;
"@ 'La simulaciÃ³n controlada de enlace vencido' | Out-Null
    Assert-Condition ((Invoke-ResetOutcome $expiryHash) -eq 'REJECTED') 'Un enlace vencido pudo utilizarse.'

    $deadlockOld = New-Sha256Hex "v011-deadlock-old-$nonce"
    Complete-RecoveryRequests @(Start-RecoveryRequest $tenantAuroraSlug $secondaryEmail $deadlockOld) 'EmisiÃ³n previa para orden de bloqueos' | Out-Null
    $deadlockLiteral = ConvertTo-SqlLiteral $deadlockOld
    $resetProcess = Start-PostgresSql $script:PsqlPath $script:DatabaseUrl 'av_app' $script:Values['AV_DB_PASSWORD'] @"
begin;
do `$`$
begin
    begin
        perform identity_auth.reset_password_with_recovery($deadlockLiteral, 'v011-synthetic-password-reset');
    exception when sqlstate '22023' then
        null;
    end;
end;
`$`$;
select pg_sleep(1);
commit;
select 'RESET_COMMITTED';
"@
    $requestProcess = Start-RecoveryRequest $tenantAuroraSlug $secondaryEmail (New-Sha256Hex "v011-deadlock-new-$nonce")
    $resetResult = Complete-PostgresSql $resetProcess 'Consumo concurrente para orden de bloqueos'
    $requestResult = Complete-PostgresSql $requestProcess 'EmisiÃ³n concurrente para orden de bloqueos'
    Assert-Condition ($resetResult.Output -match 'RESET_COMMITTED') 'El consumo concurrente no confirmÃ³.'
    Assert-Condition ($requestResult.Output -match 'RECOVERY_COMMITTED') 'La emisiÃ³n concurrente no confirmÃ³.'
    Assert-Condition ((Get-ActiveRecoveryCount $auroraSecondaryPrincipal) -eq 1) 'La prueba de orden de bloqueos no conservÃ³ un Ãºnico enlace activo.'

    foreach ($iteration in 1..3) {
        $repeatedHashes = 1..2 | ForEach-Object { New-Sha256Hex "v011-repeat-$nonce-$iteration-$_" }
        Complete-RecoveryRequests @($repeatedHashes | ForEach-Object { Start-RecoveryRequest $tenantAuroraSlug $sharedEmail $_ }) "RepeticiÃ³n concurrente $iteration" | Out-Null
        Assert-Condition ((Get-ActiveRecoveryCount $auroraSharedPrincipal) -eq 1) "La repeticiÃ³n concurrente $iteration dejÃ³ mÃ¡s de un enlace activo."
    }

    $outcome = 'PASS'
} finally {
    if ($fixtureCreated) {
        $tenantIds = @($tenantAuroraId, $tenantBosqueId) | ForEach-Object { "$(ConvertTo-SqlLiteral $_)::uuid" }
        $tenantList = $tenantIds -join ', '
        $principalList = if ($principalIds.Count -gt 0) { ($principalIds | ForEach-Object { "$(ConvertTo-SqlLiteral $_)::uuid" }) -join ', ' } else { 'null' }
        $cleanup = Invoke-PostgresSql 'av_schema_owner' @"
begin;
delete from tenant_admin.notification_outbox where related_resource_id in ($principalList);
delete from tenant_admin.audit_entry where tenant_id in ($tenantList) or actor_principal_id in ($principalList);
delete from identity_auth.one_time_token where tenant_id in ($tenantList) or principal_id in ($principalList);
delete from identity_auth.account_profile where tenant_id in ($tenantList);
delete from identity_auth.principal where id in ($principalList);
delete from tenancy.tenant where id in ($tenantList);
commit;
select case when not exists (select 1 from tenancy.tenant where id in ($tenantList)) then 'CLEANUP_CONFIRMED' else 'CLEANUP_INCOMPLETE' end;
"@ 'La limpieza del fixture V011'
        $cleanupConfirmed = $cleanup.Output -match 'CLEANUP_CONFIRMED'
    }
}

if (-not $cleanupConfirmed) {
    throw 'La limpieza de los fixtures sintÃ©ticos V011 no pudo confirmarse.'
}

Write-Output "G4_I01_V011_PASSWORD_RECOVERY=PASS;COMMIT_2=PASS;COMMIT_5=PASS;TENANT_ISOLATION=PASS;ROLLBACK=PASS;ERROR_ROLLBACK=PASS;EXPIRY=PASS;REUSE=PASS;DEADLOCK=PASS;REPEAT=PASS;CLEANUP=PASS"
