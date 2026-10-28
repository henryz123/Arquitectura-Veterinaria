[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$required = @(
    'AV_DB_URL',
    'AV_DB_USER',
    'AV_DB_PASSWORD',
    'AV_DEMO_AURORA_PASSWORD',
    'AV_DEMO_GROOMER_AURORA_EMAIL',
    'AV_DEMO_GROOMER_AURORA_PASSWORD'
)

$envPath = Join-Path $PSScriptRoot '..\.env.local'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Falta .env.local local; no se ejecutó ninguna semilla.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envPath -Encoding utf8) {
    if ($line -match '^([A-Z0-9_]+)=(.*)$') {
        $values[$matches[1]] = $matches[2]
    }
}

$missing = @($required | Where-Object { -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($values[$_]) })
if ($missing.Count -gt 0) {
    throw ('Faltan variables locales requeridas: ' + ($missing -join ', ') + '. No se ejecutó ninguna semilla.')
}

$databaseUrl = $values['AV_DB_URL'] -replace '^jdbc:'
if ($databaseUrl -notmatch '^postgresql://localhost:5432/arquitectura_veterinaria(?:\?.*)?$' -or $values['AV_DB_USER'] -ne 'av_app') {
    throw 'La semilla requiere exactamente av_app y la base local arquitectura_veterinaria. No se ejecutó ninguna operación.'
}

$email = $values['AV_DEMO_GROOMER_AURORA_EMAIL']
$password = $values['AV_DEMO_GROOMER_AURORA_PASSWORD']
$isSyntheticEmail = $email -match '^[^@\s]+@[a-z0-9.-]+\.invalid$'
$hasValidLength = $password.Length -ge 12 -and $password.Length -le 72
$hasUppercase = $password -cmatch '[A-Z]'
$hasLowercase = $password -cmatch '[a-z]'
$hasDigit = $password -match '[0-9]'
if (-not ($isSyntheticEmail -and $hasValidLength -and $hasUppercase -and $hasLowercase -and $hasDigit)) {
    throw 'Las credenciales sintéticas GROOMING no cumplen la política local. No se ejecutó ninguna operación.'
}

foreach ($name in $required) {
    Set-Item -Path "Env:$name" -Value ($values[$name])
}
$env:PGPASSWORD = $values['AV_DB_PASSWORD']
$env:PGAPPNAME = 'av-groomer-synthetic-seed'

try {
    $psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
    $seed = Join-Path $PSScriptRoot '..\database\demo\seed-groomer-synthetic.psql'
    & $psql --no-psqlrc --single-transaction --set ON_ERROR_STOP=1 --dbname=$databaseUrl --username='av_app' --file=$seed
    if ($LASTEXITCODE -ne 0) {
        throw "La semilla GROOMING terminó con código $LASTEXITCODE. La transacción fue revertida."
    }
} finally {
    foreach ($name in $required) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
    Remove-Item Env:PGPASSWORD, Env:PGAPPNAME -ErrorAction SilentlyContinue
}
