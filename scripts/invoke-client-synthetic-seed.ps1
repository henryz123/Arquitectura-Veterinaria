[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$required = @(
    'AV_DB_URL',
    'AV_MIGRATION_DB_USER',
    'AV_MIGRATION_DB_PASSWORD',
    'AV_DEMO_CLIENT_AURORA_EMAIL',
    'AV_DEMO_CLIENT_AURORA_PASSWORD',
    'AV_DEMO_CLIENT_BOSQUE_EMAIL',
    'AV_DEMO_CLIENT_BOSQUE_PASSWORD'
)

$envPath = Join-Path $PSScriptRoot '..\.env.local'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Falta .env.local local; no se ejecutó ninguna semilla.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
    if ($line -match '^([A-Z0-9_]+)=(.*)$') {
        $values[$matches[1]] = $matches[2]
    }
}

$missing = @($required | Where-Object { -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($values[$_]) })
if ($missing.Count -gt 0) {
    throw ('Faltan variables locales requeridas: ' + ($missing -join ', ') + '. No se ejecutó ninguna semilla.')
}

$databaseUrl = $values['AV_DB_URL'] -replace '^jdbc:'
$migrationUser = $values['AV_MIGRATION_DB_USER']
$migrationPassword = $values['AV_MIGRATION_DB_PASSWORD']
if ($databaseUrl -notmatch '/arquitectura_veterinaria(?:\?.*)?$') {
    throw 'AV_DB_URL no corresponde exactamente a la base local arquitectura_veterinaria. No se ejecutó ninguna semilla.'
}

foreach ($name in $required) {
    Set-Item -Path "Env:$name" -Value ($values[$name])
}
$env:PGPASSWORD = $migrationPassword
$env:PGAPPNAME = 'av-client-synthetic-seed'

try {
    $psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
    $seed = Join-Path $PSScriptRoot '..\database\demo\seed-client-synthetic.psql'
    & $psql --no-psqlrc --single-transaction --set ON_ERROR_STOP=1 --dbname=$databaseUrl --username=$migrationUser --file=$seed
    if ($LASTEXITCODE -ne 0) {
        throw "La semilla CLIENT terminó con código $LASTEXITCODE. La transacción fue revertida."
    }
} finally {
    Remove-Item Env:PGPASSWORD, Env:PGAPPNAME, Env:AV_MIGRATION_DB_PASSWORD, Env:AV_DEMO_CLIENT_AURORA_PASSWORD, Env:AV_DEMO_CLIENT_BOSQUE_PASSWORD -ErrorAction SilentlyContinue
}
